package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.dto.BatchUpdateDbParam;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.*;
import cn.cordys.crm.customer.domain.*;
import cn.cordys.crm.customer.mapper.ExtCustomerCapacityMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerOwnerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.dto.MessageDetailDTO;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.request.PoolBatchAssignRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.crm.system.notice.common.NoticeModel;
import cn.cordys.crm.system.notice.common.Receiver;
import cn.cordys.crm.system.notice.sender.insite.InSiteNoticeSender;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.service.UserExtendService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static cn.cordys.crm.customer.service.PoolCustomerBatchTypes.*;
import static cn.cordys.crm.customer.service.PoolCustomerService.DAY_MILLIS;

@Slf4j
@Service
public class PoolCustomerBatchSupport {

    private static final int SQL_BATCH_SIZE = 200;

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private BaseMapper<CustomerOwner> ownerMapper;
    @Resource
    private ExtCustomerOwnerMapper extCustomerOwnerMapper;
    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;
    @Resource
    private UserExtendService userExtendService;
    @Resource
    private LogService logService;
    @Resource
    private InSiteNoticeSender inSiteNoticeSender;
    @Resource
    private CustomerContactService customerContactService;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private CustomerOwnerHistoryService customerOwnerHistoryService;
    @Resource
    private CustomerStageService customerStageService;
    @Resource
    private CustomerWechatFriendStatusService customerWechatFriendStatusService;
    @Resource
    private CustomerMobileRuleService customerMobileRuleService;
    @Resource
    private ExtCustomerCapacityMapper extCustomerCapacityMapper;
    @Resource
    private BaseMapper<CustomerPoolPickRule> pickRuleMapper;

    public void batchDelete(List<String> ids, String userId, String orgId) {
        List<Customer> customers = customerMapper.selectByIds(ids);
        CustomerService customerService = CommonBeanFactory.getBean(CustomerService.class);
        Objects.requireNonNull(customerService).checkResourceRef(ids);
        customerService.deleteCustomerResource(ids);

        List<LogDTO> logs = customers.stream()
                .map(customer ->
                        new LogDTO(orgId, customer.getId(), userId, LogType.DELETE, LogModule.CUSTOMER_POOL, customer.getName())
                )
                .toList();
        logService.batchAdd(logs);
    }

    public void executePoolBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers, BaseField field,
                                        String currentUser, String currentOrgId) {
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey())) {
            executePoolMobileBatchUpdate(request, originCustomers, field, currentUser, currentOrgId);
            return;
        }
        customerFieldService.batchUpdate(request, field, originCustomers, Customer.class, LogModule.CUSTOMER_POOL,
                this::executeCustomerBatchUpdate, currentUser, currentOrgId);
    }

    public void executePoolMobileBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers, BaseField field,
                                              String currentUser, String currentOrgId) {
        if (CollectionUtils.isEmpty(originCustomers)) {
            return;
        }
        if (originCustomers.size() > 1) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }

        Customer originCustomer = originCustomers.get(0);
        String targetMobile = normalizeBatchUpdateMobile(request.getFieldValue());
        String originMobile = normalizeBatchUpdateMobile(originCustomer.getMobile());
        boolean mobileChanged = !Objects.equals(originMobile, targetMobile);
        if (mobileChanged && StringUtils.isNotBlank(targetMobile)) {
            customerMobileRuleService.validateForSave(originCustomer.getId(), originCustomer.getName(), targetMobile,
                    originCustomer.getCreateSource(), originCustomer.getOwner(), currentOrgId);
        }

        request.setFieldValue(targetMobile);
        addPoolBusinessFieldBatchUpdateLog(originCustomers, field, request, currentUser, currentOrgId);

        BatchUpdateDbParam updateParam = new BatchUpdateDbParam();
        updateParam.setIds(List.of(originCustomer.getId()));
        updateParam.setFieldName(field.getBusinessKey());
        updateParam.setFieldValue(targetMobile);
        updateParam.setUpdateTime(System.currentTimeMillis());
        updateParam.setUpdateUser(currentUser);
        executeCustomerBatchUpdate(updateParam);
    }

    public void executeCustomerBatchUpdate(BatchUpdateDbParam updateParam) {
        if (StringUtils.isNotBlank(updateParam.getFieldName())) {
            updateParam.setFieldName(CaseFormatUtils.camelToUnderscore(updateParam.getFieldName()));
        }
        extCustomerMapper.batchUpdateByParam(updateParam);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addPoolBusinessFieldBatchUpdateLog(List<Customer> originCustomers, BaseField field,
                                                    ResourceBatchEditRequest request, String userId, String orgId) {
        List<LogDTO> logs = originCustomers.stream().map(customer -> {
            Map originResource = new HashMap();
            Object originValue = getCustomerFieldValue(customer, field.getBusinessKey());
            if (isNotBlank(originValue)) {
                originResource.put(field.getBusinessKey(), originValue);
            }

            Map modifiedResource = new HashMap();
            if (isNotBlank(request.getFieldValue())) {
                modifiedResource.put(field.getBusinessKey(), request.getFieldValue());
            }

            LogDTO logDTO = new LogDTO(orgId, customer.getId(), userId, LogType.UPDATE, LogModule.CUSTOMER_POOL, customer.getName());
            logDTO.setOriginalValue(originResource);
            logDTO.setModifiedValue(modifiedResource);
            return logDTO;
        }).toList();
        logService.batchAdd(logs);
    }

    private Object getCustomerFieldValue(Customer customer, String fieldName) {
        try {
            return customer.getClass().getMethod("get" + CaseFormatUtils.capitalizeFirstLetter(fieldName)).invoke(customer);
        } catch (Exception ex) {
            log.error("读取客户字段失败, fieldName={}", fieldName, ex);
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean isNotBlank(Object value) {
        switch (value) {
            case null -> {
                return false;
            }
            case String str -> {
                return StringUtils.isNotBlank(str);
            }
            case List list -> {
                return CollectionUtils.isNotEmpty(list);
            }
            default -> {
                return true;
            }
        }
    }

    private String normalizeBatchUpdateMobile(Object value) {
        if (value == null) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(value).replaceAll("[\\s\\uFEFF\\xA0]+", ""));
    }

    public void sendPoolMobileBatchUpdateNotice(String taskId, CustomerPool pool, Customer customer, String currentOrgId,
                                                 String currentUser, boolean success, String errorMessage) {
        String customerName = customer == null ? "客户" : StringUtils.defaultIfBlank(customer.getName(), "客户");
        String poolName = pool == null ? "公海" : StringUtils.defaultIfBlank(pool.getName(), "公海");
        String subjectText = "公海客户手机号编辑通知";
        String resourceName = poolName + "手机号编辑：" + customerName;
        String context = success
                ? "公海客户【" + customerName + "】手机号修改成功。"
                : "公海客户【" + customerName + "】手机号修改失败，原因：" + StringUtils.defaultIfBlank(errorMessage, "未知异常");
        inSiteNoticeSender.sendAnnouncement(
                buildPoolMobileBatchUpdateMessageDetail(taskId, currentOrgId),
                buildPoolMobileBatchUpdateNoticeModel(currentUser, currentOrgId, resourceName),
                context,
                subjectText
        );
    }

    private MessageDetailDTO buildPoolMobileBatchUpdateMessageDetail(String taskId, String currentOrgId) {
        MessageDetailDTO messageDetailDTO = new MessageDetailDTO();
        messageDetailDTO.setId(taskId);
        messageDetailDTO.setEvent(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED);
        messageDetailDTO.setTaskType(NotificationConstants.Module.CUSTOMER);
        messageDetailDTO.setOrganizationId(currentOrgId);
        messageDetailDTO.setSysEnable(true);
        return messageDetailDTO;
    }

    private NoticeModel buildPoolMobileBatchUpdateNoticeModel(String currentUser, String currentOrgId, String resourceName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("organizationId", currentOrgId);
        paramMap.put("name", resourceName);
        return NoticeModel.builder()
                .operator(currentUser)
                .event(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED)
                .paramMap(paramMap)
                .receivers(List.of(new Receiver(currentUser, NotificationConstants.Type.SYSTEM_NOTICE.name())))
                .excludeSelf(false)
                .build();
    }

    public BatchPickPreparedData prepareBatchPickData(List<Customer> candidates, String currentUser, String currentOrgId,
                                                       CustomerPoolPickRule pickRule, boolean poolAdmin) {
        List<String> customerIds = candidates.stream().map(Customer::getId).filter(StringUtils::isNotBlank).toList();
        Map<String, CustomerOwner> lastOwnerMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(customerIds)) {
            LambdaQueryWrapper<CustomerOwner> ownerWrapper = new LambdaQueryWrapper<>();
            ownerWrapper.in(CustomerOwner::getCustomerId, customerIds);
            List<CustomerOwner> customerOwners = ownerMapper.selectListByLambda(ownerWrapper);
            lastOwnerMap = customerOwners.stream()
                    .collect(Collectors.groupingBy(CustomerOwner::getCustomerId))
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream()
                                    .max(Comparator.comparingLong(CustomerOwner::getCollectionTime))
                                    .orElse(null)
                    ));
        }

        List<String> mobiles = candidates.stream().map(Customer::getMobile).toList();
        Set<String> privateConflictMobiles =
                customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(mobiles, currentUser, currentOrgId);
        Set<String> ownedPoolMobiles = customerMobileRuleService.loadOwnerPoolMobiles(currentUser, currentOrgId);
        int remainingCapacity = calculateRemainingCapacity(currentUser, currentOrgId);
        Integer remainingDailyPick = null;
        if (!poolAdmin && pickRule != null && Boolean.TRUE.equals(pickRule.getLimitOnNumber())) {
            int pickedToday = countTodayPicked(currentUser);
            remainingDailyPick = Math.max(0, pickRule.getPickNumber() - pickedToday);
        }
        return new BatchPickPreparedData(lastOwnerMap, privateConflictMobiles, ownedPoolMobiles, remainingCapacity, remainingDailyPick);
    }

    public BatchPickPlan buildBatchPickPlan(List<Customer> candidates, String currentUser, CustomerPoolPickRule pickRule,
                                             boolean poolAdmin, BatchPickPreparedData preparedData) {
        if (CollectionUtils.isEmpty(candidates)) {
            return new BatchPickPlan(List.of(), List.of());
        }

        List<Customer> pickedCustomers = new ArrayList<>();
        List<String> skippedCustomerIds = new ArrayList<>();
        int remainingCapacity = preparedData.remainingCapacity();
        Integer remainingDailyPick = preparedData.remainingDailyPick();
        Set<String> ownedPoolMobiles = new HashSet<>(preparedData.ownedPoolMobiles());

        for (Customer customer : candidates) {
            if (remainingCapacity <= 0) {
                skippedCustomerIds.add(customer.getId());
                continue;
            }
            if (remainingDailyPick != null && remainingDailyPick <= 0) {
                skippedCustomerIds.add(customer.getId());
                continue;
            }
            if (!canPickCustomer(customer, currentUser, pickRule, poolAdmin, preparedData.lastOwnerMap())) {
                skippedCustomerIds.add(customer.getId());
                continue;
            }
            if (customerMobileRuleService.hasPoolImportReceiveConflict(StringUtils.trimToNull(customer.getMobile()),
                    preparedData.privateConflictMobiles(), ownedPoolMobiles)) {
                skippedCustomerIds.add(customer.getId());
                continue;
            }
            pickedCustomers.add(customer);
            remainingCapacity--;
            if (remainingDailyPick != null) {
                remainingDailyPick--;
            }
            customerMobileRuleService.addOwnedMobile(customer, ownedPoolMobiles);
        }
        return new BatchPickPlan(pickedCustomers, skippedCustomerIds);
    }

    private boolean canPickCustomer(Customer customer, String ownerId, CustomerPoolPickRule pickRule, boolean poolAdmin,
                                    Map<String, CustomerOwner> lastOwnerMap) {
        if (customer == null) {
            return false;
        }
        if (poolAdmin || pickRule == null) {
            return true;
        }
        if (Boolean.TRUE.equals(pickRule.getLimitNew())) {
            LocalDateTime joinPoolTime = Instant.ofEpochMilli(customer.getUpdateTime())
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime releaseDate = joinPoolTime.plusDays(pickRule.getNewPickInterval());
            if (releaseDate.isAfter(LocalDateTime.now())) {
                return false;
            }
        }
        if (Boolean.TRUE.equals(pickRule.getLimitPreOwner())) {
            CustomerOwner lastOwner = lastOwnerMap.get(customer.getId());
            if (lastOwner != null && Strings.CS.equals(lastOwner.getOwner(), ownerId)) {
                long nextPickMillis = lastOwner.getEndTime() + pickRule.getPickIntervalDays() * DAY_MILLIS;
                return System.currentTimeMillis() >= nextPickMillis;
            }
        }
        return true;
    }

    public void executeBatchPickPlan(String taskId, CustomerPool pool, BatchPickPlan plan, Map<String, String> recentOwnerMap,
                                      String currentUser, String currentOrgId, String defaultStage, String defaultStageStatus) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        List<String> pickedCustomerIds = plan.pickedCustomers().stream().map(Customer::getId).toList();
        for (List<String> partitionIds : partition(pickedCustomerIds, SQL_BATCH_SIZE)) {
            extCustomerMapper.batchAssignToOwner(partitionIds, currentUser, currentUser, now, now, defaultStage, defaultStageStatus);
        }
        log.info("[POOL_BATCH_PICK_UPDATE_CUSTOMER_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - updateCustomerStart);

        long deleteOwnerHistoryStart = System.currentTimeMillis();
        customerOwnerHistoryService.deleteByCustomerIds(pickedCustomerIds);
        log.info("[POOL_BATCH_PICK_DELETE_OWNER_HISTORY_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - deleteOwnerHistoryStart);

        long updateContactStart = System.currentTimeMillis();
        Map<String, List<String>> oldOwnerCustomerIdsMap = new LinkedHashMap<>();
        for (Customer customer : plan.pickedCustomers()) {
            String oldOwner = recentOwnerMap.get(customer.getId());
            String ownerKey = StringUtils.defaultString(oldOwner);
            oldOwnerCustomerIdsMap.computeIfAbsent(ownerKey, key -> new ArrayList<>()).add(customer.getId());
        }
        for (Map.Entry<String, List<String>> oldOwnerEntry : oldOwnerCustomerIdsMap.entrySet()) {
            String oldOwner = StringUtils.trimToNull(oldOwnerEntry.getKey());
            for (List<String> partitionIds : partition(oldOwnerEntry.getValue(), SQL_BATCH_SIZE)) {
                customerContactService.batchUpdatePoolContactOwner(partitionIds, currentUser, oldOwner, currentOrgId);
            }
        }
        log.info("[POOL_BATCH_PICK_UPDATE_CONTACT_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - updateContactStart);

        long recalculateStart = System.currentTimeMillis();
        customerWechatFriendStatusService.recalculateCustomers(pickedCustomerIds, currentUser);
        log.info("[POOL_BATCH_PICK_RECALCULATE_WECHAT_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - recalculateStart);

        long logStart = System.currentTimeMillis();
        List<LogDTO> logs = plan.pickedCustomers().stream()
                .map(customer -> new LogDTO(currentOrgId, customer.getId(), currentUser, LogType.PICK,
                        LogModule.CUSTOMER_POOL, customer.getName()))
                .toList();
        logService.batchAdd(logs);
        log.info("[POOL_BATCH_PICK_LOG_BATCH_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - logStart);
    }

    public CustomerPoolPickRule loadPoolPickRule(String poolId) {
        LambdaQueryWrapper<CustomerPoolPickRule> pickRuleWrapper = new LambdaQueryWrapper<>();
        pickRuleWrapper.eq(CustomerPoolPickRule::getPoolId, poolId);
        List<CustomerPoolPickRule> customerPoolPickRules = pickRuleMapper.selectListByLambda(pickRuleWrapper);
        return CollectionUtils.isEmpty(customerPoolPickRules) ? null : customerPoolPickRules.getFirst();
    }

    private int countTodayPicked(String ownerId) {
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getInSharedPool, false)
                .between(Customer::getCollectionTime, TimeUtils.getTodayStart(), TimeUtils.getTodayStart() + DAY_MILLIS);
        return customerMapper.selectListByLambda(customerWrapper).size();
    }

    private int calculateRemainingCapacity(String ownerId, String currentOrgId) {
        CustomerCapacity customerCapacity = getUserCapacity(ownerId, currentOrgId);
        if (customerCapacity == null || customerCapacity.getCapacity() == null) {
            return Integer.MAX_VALUE;
        }
        List<String> excludeStageIds = new ArrayList<>();
        String paymentStageId = customerStageService.getPaymentStageId(currentOrgId);
        String failStageId = customerStageService.getFailStageId(currentOrgId);
        if (StringUtils.isNotEmpty(paymentStageId)) {
            excludeStageIds.add(paymentStageId);
        }
        if (StringUtils.isNotEmpty(failStageId)) {
            excludeStageIds.add(failStageId);
        }
        int excludeCount = 0;
        if (CollectionUtils.isNotEmpty(excludeStageIds)) {
            excludeCount = extCustomerMapper.countByOwnerAndStages(ownerId, excludeStageIds);
        }
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.eq(Customer::getOwner, ownerId).eq(Customer::getInSharedPool, false);
        int ownCount = customerMapper.selectListByLambda(customerWrapper).size();
        return Math.max(0, customerCapacity.getCapacity() - (ownCount - excludeCount));
    }

    public BatchTransferPlan buildBatchTransferPlan(List<String> requestIds, List<Customer> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return new BatchTransferPlan(List.of(), CollectionUtils.isEmpty(requestIds) ? List.of() : new ArrayList<>(requestIds));
        }
        List<Customer> transferCustomers = candidates.stream().filter(Objects::nonNull).toList();
        Set<String> transferIdSet = transferCustomers.stream().map(Customer::getId).collect(Collectors.toSet());
        List<String> skippedCustomerIds = CollectionUtils.isEmpty(requestIds)
                ? List.of()
                : requestIds.stream().filter(StringUtils::isNotBlank).filter(id -> !transferIdSet.contains(id)).toList();
        return new BatchTransferPlan(transferCustomers, skippedCustomerIds);
    }

    public void executeBatchTransferPlan(String taskId, CustomerPool sourcePool, CustomerPool targetPool, BatchTransferPlan plan,
                                          String currentUser, String currentOrgId) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        List<String> transferIds = plan.transferCustomers().stream().map(Customer::getId).toList();
        for (List<String> partitionIds : partition(transferIds, SQL_BATCH_SIZE)) {
            extCustomerMapper.batchTransferToPool(partitionIds, targetPool.getId(), currentUser, now);
        }
        log.info("[POOL_BATCH_TRANSFER_UPDATE_CUSTOMER_COST] taskId={}, sourcePoolId={}, costMs={}",
                taskId, sourcePool.getId(), System.currentTimeMillis() - updateCustomerStart);

        long logStart = System.currentTimeMillis();
        List<LogDTO> logs = plan.transferCustomers().stream()
                .map(customer -> new LogDTO(currentOrgId, customer.getId(), currentUser, LogType.UPDATE,
                        LogModule.CUSTOMER_POOL, Translator.getWithArgs("pool_transfer_log", customer.getName(), targetPool.getName())))
                .toList();
        logService.batchAdd(logs);
        log.info("[POOL_BATCH_TRANSFER_LOG_BATCH_COST] taskId={}, sourcePoolId={}, costMs={}",
                taskId, sourcePool.getId(), System.currentTimeMillis() - logStart);
    }

    /**
     * 预加载批量分配决策所需的用户侧数据。
     * 这里的目标是把“循环里查数据库”提前挪到循环外，避免 2000 条客户逐条打库。
     */
    public BatchAssignPreparedData prepareBatchAssignData(List<String> assignUserIds, List<Customer> candidates, String currentOrgId) {
        Map<String, Integer> userCapacitiesMap = new HashMap<>();
        Map<String, Set<String>> userOwnedPoolMobileMap = new HashMap<>();
        Map<String, Set<String>> userPrivateConflictMobileMap = new HashMap<>();
        List<String> candidateMobiles = candidates.stream().map(Customer::getMobile).toList();
        List<String> excludeStageIds = new ArrayList<>();
        String paymentStageId = customerStageService.getPaymentStageId(currentOrgId);
        String failStageId = customerStageService.getFailStageId(currentOrgId);
        if (StringUtils.isNotEmpty(paymentStageId)) {
            excludeStageIds.add(paymentStageId);
        }
        if (StringUtils.isNotEmpty(failStageId)) {
            excludeStageIds.add(failStageId);
        }

        for (String targetUserId : assignUserIds) {
            CustomerCapacity customerCapacity = getUserCapacity(targetUserId, currentOrgId);
            int remainingCapacity = Integer.MAX_VALUE;
            if (customerCapacity != null && customerCapacity.getCapacity() != null) {
                int excludeCount = 0;
                if (CollectionUtils.isNotEmpty(excludeStageIds)) {
                    excludeCount = extCustomerMapper.countByOwnerAndStages(targetUserId, excludeStageIds);
                }
                LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
                customerWrapper.eq(Customer::getOwner, targetUserId).eq(Customer::getInSharedPool, false);
                int ownCount = customerMapper.selectListByLambda(customerWrapper).size();
                remainingCapacity = Math.max(0, customerCapacity.getCapacity() - (ownCount - excludeCount));
            }
            userCapacitiesMap.put(targetUserId, remainingCapacity);
            // 复用旧批量分配的重复规则：
            // 预加载目标负责人名下“公海来源客户已占用手机号”集合，后面逐条分配时直接命中判断
            userOwnedPoolMobileMap.put(targetUserId, customerMobileRuleService.loadOwnerPoolMobiles(targetUserId, currentOrgId));
            // 复用旧批量分配的重复规则：
            // 预加载目标负责人名下“私海来源客户冲突手机号”集合，避免把公海客户分配到会撞号的负责人
            userPrivateConflictMobileMap.put(targetUserId,
                    customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(candidateMobiles, targetUserId, currentOrgId));
        }
        return new BatchAssignPreparedData(userCapacitiesMap, userOwnedPoolMobileMap, userPrivateConflictMobileMap);
    }

    /**
     * 在内存中完成轮询分配，不落库。
     * 输出的是一个最终分配方案，后续批量 SQL 和批量通知都只依赖这个方案。
     */
    public PoolCustomerBatchTypes.BatchAssignPlan buildBatchAssignPlan(List<Customer> candidates, List<String> assignUserIds,
                                                 BatchAssignPreparedData preparedData, Map<String, String> recentOwnerMap) {
        Map<String, List<Customer>> ownerCustomersMap = new LinkedHashMap<>();
        List<String> assignedCustomerIds = new ArrayList<>();
        List<String> unassignedCustomerIds = new ArrayList<>();
        if (CollectionUtils.isEmpty(candidates) || CollectionUtils.isEmpty(assignUserIds)) {
            candidates.stream().map(Customer::getId).filter(Objects::nonNull).forEach(unassignedCustomerIds::add);
            return new PoolCustomerBatchTypes.BatchAssignPlan(ownerCustomersMap, assignedCustomerIds, unassignedCustomerIds, recentOwnerMap);
        }

        int userIdx = 0;
        int userCount = assignUserIds.size();
        for (Customer customer : candidates) {
            boolean success = false;
            int attempts = 0;
            while (attempts < userCount) {
                // 按用户轮询尝试，保持旧逻辑“多负责人轮流分配”的业务语义
                String currentUserId = assignUserIds.get(userIdx);
                Integer capacity = preparedData.userCapacitiesMap().get(currentUserId);
                Set<String> ownedPoolMobiles = preparedData.userOwnedPoolMobileMap().get(currentUserId);
                Set<String> privateConflictMobiles = preparedData.userPrivateConflictMobileMap().get(currentUserId);
                if (capacity != null && capacity > 0
                        // 复用旧批量分配的最终重复规则判断：
                        // 只要当前客户手机号命中“私海冲突集合”或“公海来源已占用集合”，本轮就不给这个负责人
                        && !customerMobileRuleService.hasPoolImportReceiveConflict(StringUtils.trimToNull(customer.getMobile()),
                        privateConflictMobiles, ownedPoolMobiles)) {
                    ownerCustomersMap.computeIfAbsent(currentUserId, key -> new ArrayList<>()).add(customer);
                    assignedCustomerIds.add(customer.getId());
                    preparedData.userCapacitiesMap().put(currentUserId, capacity - 1);
                    // 复用旧批量分配的批内防重逻辑：
                    // 当前客户一旦成功分给该负责人，立刻把手机号加入内存占用集合，防止同一批后续客户再撞号
                    customerMobileRuleService.addOwnedMobile(customer, ownedPoolMobiles);
                    userIdx = (userIdx + 1) % userCount;
                    success = true;
                    break;
                }
                userIdx = (userIdx + 1) % userCount;
                attempts++;
            }
            if (!success) {
                unassignedCustomerIds.add(customer.getId());
            }
        }
        return new PoolCustomerBatchTypes.BatchAssignPlan(ownerCustomersMap, assignedCustomerIds, unassignedCustomerIds, recentOwnerMap);
    }

    /**
     * 按分配方案批量落库。
     * 这里不再复用旧的 ownCustomer()，而是把客户更新、负责人历史删除、联系人负责人更新、
     * 微信好友状态重算、日志写入拆成批量动作。
     */
    public void executeBatchAssignPlan(String taskId, CustomerPool pool, PoolCustomerBatchTypes.BatchAssignPlan plan, String currentOrgId,
                                        String currentUser, String defaultStage, String defaultStageStatus) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<Customer>> entry : plan.getOwnerCustomersMap().entrySet()) {
            String ownerId = entry.getKey();
            List<String> customerIds = entry.getValue().stream().map(Customer::getId).toList();
            for (List<String> partitionIds : partition(customerIds, SQL_BATCH_SIZE)) {
                // 同一个负责人一批一批更新客户，避免单条 update 和超大 SQL
                extCustomerMapper.batchAssignToOwner(partitionIds, ownerId, ownerId, now, now, defaultStage, defaultStageStatus);
            }
        }
        log.info("[POOL_BATCH_ASSIGN_UPDATE_CUSTOMER_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - updateCustomerStart);

        long deleteOwnerHistoryStart = System.currentTimeMillis();
        customerOwnerHistoryService.deleteByCustomerIds(plan.getAssignedCustomerIds());
        log.info("[POOL_BATCH_ASSIGN_DELETE_OWNER_HISTORY_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - deleteOwnerHistoryStart);

        long updateContactStart = System.currentTimeMillis();
        for (Map.Entry<String, List<Customer>> ownerEntry : plan.getOwnerCustomersMap().entrySet()) {
            String newOwner = ownerEntry.getKey();
            Map<String, List<String>> oldOwnerCustomerIdsMap = new LinkedHashMap<>();
            for (Customer customer : ownerEntry.getValue()) {
                String oldOwner = plan.getRecentOwnerMap().get(customer.getId());
                String ownerKey = StringUtils.defaultString(oldOwner);
                oldOwnerCustomerIdsMap.computeIfAbsent(ownerKey, key -> new ArrayList<>()).add(customer.getId());
            }
            for (Map.Entry<String, List<String>> oldOwnerEntry : oldOwnerCustomerIdsMap.entrySet()) {
                String oldOwner = StringUtils.trimToNull(oldOwnerEntry.getKey());
                for (List<String> partitionIds : partition(oldOwnerEntry.getValue(), SQL_BATCH_SIZE)) {
                    // 联系人负责人只同步“最近负责人/空负责人/无效负责人”那部分记录
                    customerContactService.batchUpdatePoolContactOwner(partitionIds, newOwner, oldOwner, currentOrgId);
                }
            }
        }
        log.info("[POOL_BATCH_ASSIGN_UPDATE_CONTACT_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - updateContactStart);

        long recalculateStart = System.currentTimeMillis();
        customerWechatFriendStatusService.recalculateCustomers(plan.getAssignedCustomerIds(), currentUser);
        log.info("[POOL_BATCH_ASSIGN_RECALCULATE_WECHAT_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - recalculateStart);

        long logStart = System.currentTimeMillis();
        List<LogDTO> logs = plan.getOwnerCustomersMap().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(customer -> new LogDTO(currentOrgId, customer.getId(), currentUser, LogType.ASSIGN,
                                LogModule.CUSTOMER_POOL, customer.getName())))
                .toList();
        logService.batchAdd(logs);
        log.info("[POOL_BATCH_ASSIGN_LOG_BATCH_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - logStart);
    }

    /**
     * 聚合通知：
     * 每个负责人只发一条通知，而不是每个客户一条，降低通知量和异步消息压力。
     */
    public void sendBatchAssignSummaryNotice(String taskId, CustomerPool pool, PoolCustomerBatchTypes.BatchAssignPlan plan, String currentOrgId, String currentUser) {
        long noticeStart = System.currentTimeMillis();
        for (Map.Entry<String, List<Customer>> entry : plan.getOwnerCustomersMap().entrySet()) {
            String ownerId = entry.getKey();
            List<Customer> customers = entry.getValue();
            if (CollectionUtils.isEmpty(customers)) {
                continue;
            }
            String resourceName = buildBatchAssignNoticeResourceName(pool, customers);
            // 这里不再走通用通知中心分发链路，避免 message task 配置再次扩散成多条通知。
            // 批量分配场景只需要“一人一条站内汇总通知”。
            inSiteNoticeSender.sendAnnouncement(
                    buildBatchAssignMessageDetail(taskId, currentOrgId),
                    buildBatchAssignNoticeModel(currentUser, currentOrgId, ownerId, resourceName),
                    resourceName,
                    "公海客户分配通知"
            );
        }
        log.info("[POOL_BATCH_ASSIGN_SEND_NOTICE_COST] taskId={}, poolId={}, costMs={}",
                taskId, pool.getId(), System.currentTimeMillis() - noticeStart);
    }

    private MessageDetailDTO buildBatchAssignMessageDetail(String taskId, String currentOrgId) {
        MessageDetailDTO messageDetailDTO = new MessageDetailDTO();
        messageDetailDTO.setId(taskId);
        messageDetailDTO.setEvent(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED);
        messageDetailDTO.setTaskType(NotificationConstants.Module.CUSTOMER);
        messageDetailDTO.setOrganizationId(currentOrgId);
        messageDetailDTO.setSysEnable(true);
        return messageDetailDTO;
    }

    private NoticeModel buildBatchAssignNoticeModel(String currentUser, String currentOrgId, String ownerId, String resourceName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("organizationId", currentOrgId);
        paramMap.put("name", resourceName);
        return NoticeModel.builder()
                .operator(currentUser)
                .event(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED)
                .paramMap(paramMap)
                .receivers(List.of(new Receiver(ownerId, NotificationConstants.Type.SYSTEM_NOTICE.name())))
                .excludeSelf(true)
                .build();
    }

    /**
     * 构造聚合通知文案，保留少量客户名称摘要，避免通知内容过长。
     */
    private String buildBatchAssignNoticeResourceName(CustomerPool pool, List<Customer> customers) {
        int total = customers.size();
        List<String> names = customers.stream()
                .map(Customer::getName)
                .filter(StringUtils::isNotBlank)
                .limit(3)
                .toList();
        String customerSummary = CollectionUtils.isEmpty(names) ? "客户" : String.join("、", names);
        if (total > names.size()) {
            customerSummary = customerSummary + "等" + total + "个客户";
        }
        return StringUtils.defaultString(pool.getName(), "公海") + "分配：" + customerSummary;
    }

    /**
     * 一次性查出所有客户最近一次销售负责人。
     * 后续批量更新联系人负责人时会按“新负责人 + 最近负责人”分组使用。
     */
    public Map<String, String> buildRecentOwnerMap(List<Customer> customers) {
        if (CollectionUtils.isEmpty(customers)) {
            return new HashMap<>();
        }
        List<String> customerIds = customers.stream().map(Customer::getId).filter(StringUtils::isNotBlank).toList();
        Map<String, String> recentOwnerMap = new LinkedHashMap<>();
        for (CustomerOwner customerOwner : extCustomerOwnerMapper.listRecentOwners(customerIds)) {
            recentOwnerMap.putIfAbsent(customerOwner.getCustomerId(), customerOwner.getOwner());
        }
        return recentOwnerMap;
    }

    /**
     * 统一解析前端传入的分配用户集合，兼容旧接口的单用户字段。
     */
    public List<String> resolveAssignUserIds(PoolBatchAssignRequest request, String assignUserId) {
        List<String> assignUserIds = request.getAssignUserIds();
        if (CollectionUtils.isEmpty(assignUserIds) && StringUtils.isNotBlank(assignUserId)) {
            assignUserIds = List.of(assignUserId);
        }
        if (CollectionUtils.isEmpty(assignUserIds)) {
            return List.of();
        }
        return assignUserIds.stream().filter(StringUtils::isNotBlank).distinct().toList();
    }

    /**
     * 从本次选中的客户里反推出所属公海池，并强制要求只能来自同一个公海池。
     */
    public String resolvePoolIdForBatchAssign(List<Customer> customers) {
        List<String> poolIds = customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                .map(Customer::getPoolId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(poolIds)) {
            throw new GenericException("所选客户不在公海中，无法批量分配");
        }
        if (poolIds.size() > 1) {
            throw new GenericException("批量分配仅支持同一公海池内的客户");
        }
        return poolIds.getFirst();
    }

    private String resolvePoolIdForBatchPick(List<Customer> customers, String requestPoolId) {
        List<String> poolIds = customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                .map(Customer::getPoolId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(poolIds)) {
            throw new GenericException("所选客户不在公海中，无法批量领取");
        }
        if (poolIds.size() > 1) {
            throw new GenericException("按筛选批量领取仅支持同一公海池内的客户");
        }
        String actualPoolId = poolIds.getFirst();
        if (StringUtils.isNotBlank(requestPoolId) && !Strings.CS.equals(requestPoolId, actualPoolId)) {
            throw new GenericException("领取客户所属公海池已变化，请刷新后重试");
        }
        return actualPoolId;
    }

    /**
     * 从本次选中的客户里反推出源公海池，并强制要求只能来自同一个公海池。
     */
    public String resolveSourcePoolIdForBatchTransfer(List<Customer> customers) {
        List<String> poolIds = customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                .map(Customer::getPoolId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(poolIds)) {
            throw new GenericException("所选客户不在公海中，无法批量转移");
        }
        if (poolIds.size() > 1) {
            throw new GenericException("按筛选批量转移仅支持同一公海池内的客户");
        }
        return poolIds.getFirst();
    }


    public void executeBatchPickPlanChunk(String taskId, CustomerPool pool, List<Customer> pickedChunk,
                                          Map<String, String> recentOwnerMap, String currentUser, String currentOrgId,
                                          String defaultStage, String defaultStageStatus) {
        if (CollectionUtils.isEmpty(pickedChunk)) {
            return;
        }
        BatchPickPlan chunkPlan = new BatchPickPlan(pickedChunk, List.of());
        executeBatchPickPlan(taskId, pool, chunkPlan, recentOwnerMap, currentUser, currentOrgId, defaultStage, defaultStageStatus);
    }

    public void executeBatchAssignPlanChunk(String taskId, CustomerPool pool, String ownerId, List<Customer> customers,
                                            Map<String, String> recentOwnerMap, String currentOrgId, String currentUser,
                                            String defaultStage, String defaultStageStatus) {
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        Map<String, List<Customer>> ownerCustomersMap = Map.of(ownerId, customers);
        PoolCustomerBatchTypes.BatchAssignPlan chunkPlan = new PoolCustomerBatchTypes.BatchAssignPlan(ownerCustomersMap,
                customers.stream().map(Customer::getId).toList(), List.of(), recentOwnerMap);
        executeBatchAssignPlan(taskId, pool, chunkPlan, currentOrgId, currentUser, defaultStage, defaultStageStatus);
    }

    public void executeBatchTransferPlanChunk(String taskId, CustomerPool sourcePool, CustomerPool targetPool,
                                              List<Customer> transferChunk, String currentUser, String currentOrgId) {
        if (CollectionUtils.isEmpty(transferChunk)) {
            return;
        }
        BatchTransferPlan chunkPlan = new BatchTransferPlan(transferChunk, List.of());
        executeBatchTransferPlan(taskId, sourcePool, targetPool, chunkPlan, currentUser, currentOrgId);
    }

    public List<List<String>> partition(List<String> ids, int batchSize) {
        List<List<String>> partitions = new ArrayList<>();
        if (CollectionUtils.isEmpty(ids)) {
            return partitions;
        }
        for (int index = 0; index < ids.size(); index += batchSize) {
            partitions.add(ids.subList(index, Math.min(index + batchSize, ids.size())));
        }
        return partitions;
    }

    private CustomerCapacity getUserCapacity(String userId, String organizationId) {
        List<String> scopeIds = userExtendService.getUserScopeIds(userId, organizationId);
        return extCustomerCapacityMapper.getCapacityByScopeIds(scopeIds, organizationId);
    }

}
