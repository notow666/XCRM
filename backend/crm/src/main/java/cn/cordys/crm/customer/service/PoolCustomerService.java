package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.dto.ChartAnalysisDbRequest;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.chart.ChartResult;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.service.BaseChartService;
import cn.cordys.common.util.*;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.crm.customer.domain.*;
import cn.cordys.crm.customer.dto.CustomerPoolDTO;
import cn.cordys.crm.customer.dto.CustomerPoolPickRuleDTO;
import cn.cordys.crm.customer.dto.CustomerPoolRecycleRuleDTO;
import cn.cordys.crm.customer.dto.MobileConflictDTO;
import cn.cordys.crm.customer.dto.request.CustomerChartAnalysisDbRequest;
import cn.cordys.crm.customer.dto.request.CustomerPageRequest;
import cn.cordys.crm.customer.dto.request.PoolCustomerChartAnalysisRequest;
import cn.cordys.crm.customer.dto.request.PoolCustomerPickRequest;
import cn.cordys.crm.customer.mapper.ExtCustomerCapacityMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerOwnerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.follow.service.FollowUpPlanService;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.FilterConditionDTO;
import cn.cordys.crm.system.dto.RuleConditionDTO;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.request.PoolBatchAssignRequest;
import cn.cordys.crm.system.dto.request.PoolBatchPickRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.notice.CommonNoticeSendService;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.crm.system.service.UserExtendService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PoolCustomerService {

    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000;
    private static final int BATCH_DELETE_BY_CONDITION_SIZE = 500;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private BaseMapper<User> userMapper;
    @Resource
    private BaseMapper<CustomerOwner> ownerMapper;
    @Resource
    private BaseMapper<CustomerPool> poolMapper;
    @Resource
    private BaseMapper<CustomerPoolPickRule> pickRuleMapper;
    @Resource
    private BaseMapper<CustomerPoolRecycleRule> recycleRuleMapper;
    @Resource
    private ExtCustomerCapacityMapper extCustomerCapacityMapper;
    @Resource
    private UserExtendService userExtendService;
    @Resource
    private LogService logService;
    @Resource
    private CommonNoticeSendService commonNoticeSendService;
    @Resource
    private CustomerPoolService customerPoolService;
    @Resource
    private ModuleFormCacheService moduleFormCacheService;
    @Resource
    private CustomerContactService customerContactService;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private BaseChartService baseChartService;
    @Resource
    private ExtCustomerOwnerMapper extCustomerOwnerMapper;
    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;
    @Resource
    private FollowUpPlanService followUpPlanService;
    @Resource
    private CustomerOwnerHistoryService customerOwnerHistoryService;
    @Resource
    private CustomerStageService customerStageService;

    /**
     * 获取当前用户公海选项
     *
     * @param currentUser  当前用户ID
     * @param currentOrgId 当前组织ID
     *
     * @return 公海选项
     */
    public List<CustomerPoolDTO> getPoolOptions(String currentUser, String currentOrgId) {
        List<CustomerPoolDTO> options = new ArrayList<>();
        LambdaQueryWrapper<CustomerPool> poolWrapper = new LambdaQueryWrapper<>();
        poolWrapper.eq(CustomerPool::getEnable, true)
                .eq(CustomerPool::getOrganizationId, currentOrgId)
                .orderByDesc(CustomerPool::getUpdateTime);

        List<CustomerPool> pools = poolMapper.selectListByLambda(poolWrapper);
        if (CollectionUtils.isEmpty(pools)) {
            return options;
        }

        List<String> userIds = pools.stream()
                .flatMap(pool ->
                        Stream.of(pool.getCreateUser(), pool.getUpdateUser())).toList();

        List<User> createOrUpdateUsers = userMapper.selectByIds(userIds.toArray(new String[0]));
        Map<String, String> userMap = createOrUpdateUsers.stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<String> poolIds = pools.stream()
                .map(CustomerPool::getId)
                .toList();
        LambdaQueryWrapper<CustomerPoolPickRule> pickRuleWrapper = new LambdaQueryWrapper<>();
        pickRuleWrapper.in(CustomerPoolPickRule::getPoolId, poolIds);

        List<CustomerPoolPickRule> pickRules = pickRuleMapper.selectListByLambda(pickRuleWrapper);
        Map<String, CustomerPoolPickRule> pickRuleMap = pickRules.stream()
                .collect(Collectors.toMap(CustomerPoolPickRule::getPoolId, pickRule -> pickRule));

        LambdaQueryWrapper<CustomerPoolRecycleRule> recycleRuleWrapper = new LambdaQueryWrapper<>();
        recycleRuleWrapper.in(CustomerPoolRecycleRule::getPoolId, poolIds);

        List<CustomerPoolRecycleRule> recycleRules = recycleRuleMapper.selectListByLambda(recycleRuleWrapper);
        Map<String, CustomerPoolRecycleRule> recycleRuleMap = recycleRules.stream()
                .collect(Collectors.toMap(CustomerPoolRecycleRule::getPoolId, recycleRule -> recycleRule));

        Map<String, List<CustomerPoolHiddenField>> hiddenFieldMap = customerPoolService.getCustomerPoolHiddenFieldByPoolIds(poolIds)
                .stream()
                .collect(Collectors.groupingBy(CustomerPoolHiddenField::getPoolId));

        List<BaseField> fields = moduleFormCacheService.getBusinessFormConfig(FormKey.CUSTOMER.getKey(), currentOrgId).getFields();


        pools.forEach(pool -> {
            List<String> scopeIds = userExtendService.getScopeOwnerIds(JSON.parseArray(pool.getScopeId(), String.class), currentOrgId);
            List<String> ownerIds = userExtendService.getScopeOwnerIds(JSON.parseArray(pool.getOwnerId(), String.class), currentOrgId);
            if (scopeIds.contains(currentUser) || ownerIds.contains(currentUser) || Strings.CS.equals(currentUser, InternalUser.ADMIN.getValue())) {
                CustomerPoolDTO poolDTO = new CustomerPoolDTO();
                BeanUtils.copyBean(poolDTO, pool);

                poolDTO.setMembers(userExtendService.getScope(JSON.parseArray(pool.getScopeId(), String.class)));
                poolDTO.setOwners(userExtendService.getScope(JSON.parseArray(pool.getOwnerId(), String.class)));
                poolDTO.setCreateUserName(userMap.get(pool.getCreateUser()));
                poolDTO.setUpdateUserName(userMap.get(pool.getUpdateUser()));

                CustomerPoolPickRuleDTO pickRule = new CustomerPoolPickRuleDTO();
                BeanUtils.copyBean(pickRule, pickRuleMap.get(pool.getId()));
                CustomerPoolRecycleRuleDTO recycleRule = new CustomerPoolRecycleRuleDTO();
                CustomerPoolRecycleRule customerPoolRecycleRule = recycleRuleMap.get(pool.getId());
                BeanUtils.copyBean(recycleRule, customerPoolRecycleRule);
                recycleRule.setConditions(JSON.parseArray(customerPoolRecycleRule.getCondition(), RuleConditionDTO.class));
                poolDTO.setPickRule(pickRule);
                poolDTO.setRecycleRule(recycleRule);
                poolDTO.setEditable(ownerIds.contains(currentUser));

                Set<String> hiddenFieldIds;
                if (hiddenFieldMap.get(pool.getId()) != null) {
                    hiddenFieldIds = hiddenFieldMap.get(pool.getId()).stream()
                            .map(CustomerPoolHiddenField::getFieldId)
                            .collect(Collectors.toSet());
                } else {
                    hiddenFieldIds = Set.of();
                }

                poolDTO.setFieldConfigs(customerPoolService.getFieldConfigs(fields, hiddenFieldIds));

                options.add(poolDTO);
            }
        });
        return options;
    }

    /**
     * 领取客户
     *
     * @param request      请求参数
     * @param currentUser  当前用户ID
     * @param currentOrgId 当前组织ID
     */
    public void pick(PoolCustomerPickRequest request, String currentUser, String currentOrgId) {
        CustomerPool pool = poolMapper.selectByPrimaryKey(request.getPoolId());
        validateCapacity(1, currentUser, currentOrgId);
        LambdaQueryWrapper<CustomerPoolPickRule> pickRuleWrapper = new LambdaQueryWrapper<>();
        pickRuleWrapper.eq(CustomerPoolPickRule::getPoolId, request.getPoolId());
        List<CustomerPoolPickRule> customerPoolPickRules = pickRuleMapper.selectListByLambda(pickRuleWrapper);
        CustomerPoolPickRule pickRule = customerPoolPickRules.getFirst();
        boolean poolAdmin = userExtendService.isPoolAdmin(JSON.parseArray(pool.getOwnerId(), String.class), currentUser, currentOrgId);
        if (!poolAdmin) {
            validateDailyPickNum(1, currentUser, pickRule);
        }
        ownCustomer(request.getCustomerId(), currentUser, pickRule, currentUser, LogType.PICK, currentOrgId, poolAdmin);
    }

    /**
     * 分配客户
     *
     * @param id           客户ID
     * @param assignUserId 分配用户ID
     */
    public void assign(String id, String assignUserId, String currentOrgId, String currentUser) {
        validateCapacity(1, assignUserId, currentOrgId);
        ownCustomer(id, assignUserId, null, currentUser, LogType.ASSIGN, currentOrgId, false);
    }

    /**
     * 删除客户
     *
     * @param id 客户ID
     */
    @OperationLog(module = LogModule.CUSTOMER_POOL, type = LogType.DELETE, resourceId = "{#id}")
    public void delete(String id) {
        Customer customer = customerMapper.selectByPrimaryKey(id);
        CustomerService customerService = CommonBeanFactory.getBean(CustomerService.class);
        Objects.requireNonNull(customerService).checkResourceRef(List.of(id));
        customerService.deleteCustomerResource(List.of(id));

        // 设置操作对象
        OperationLogContext.setResourceName(customer.getName());
    }

    /**
     * 批量领取客户
     *
     * @param request      请求参数
     * @param currentUser  当前用户ID
     * @param currentOrgId 当前组织ID
     */
    public void batchPick(PoolBatchPickRequest request, String currentUser, String currentOrgId) {
        CustomerPool pool = poolMapper.selectByPrimaryKey(request.getPoolId());
        validateCapacity(request.getBatchIds().size(), currentUser, currentOrgId);
        LambdaQueryWrapper<CustomerPoolPickRule> pickRuleWrapper = new LambdaQueryWrapper<>();
        pickRuleWrapper.eq(CustomerPoolPickRule::getPoolId, request.getPoolId());
        List<CustomerPoolPickRule> customerPoolPickRules = pickRuleMapper.selectListByLambda(pickRuleWrapper);
        CustomerPoolPickRule pickRule = customerPoolPickRules.getFirst();
        boolean poolAdmin = userExtendService.isPoolAdmin(JSON.parseArray(pool.getOwnerId(), String.class), currentUser, currentOrgId);
        if (!poolAdmin) {
            validateDailyPickNum(request.getBatchIds().size(), currentUser, pickRule);
        }
        request.getBatchIds().forEach(id -> ownCustomer(id, currentUser, pickRule, currentUser, LogType.PICK, currentOrgId, poolAdmin));
    }

    /**
     * 批量分配客户
     *
     * @param request      请求参数
     * @param assignUserId 分配用户ID（单个，兼容旧接口）
     * @param currentOrgId 当前组织ID
     * @return 未分配的客户数量，0表示全部分配完成
     */
     public int batchAssign(PoolBatchAssignRequest request, String assignUserId, String currentOrgId, String currentUser) {
         List<String> assignUserIds = request.getAssignUserIds();
         if (CollectionUtils.isEmpty(assignUserIds)) {
             if (StringUtils.isNotEmpty(assignUserId)) {
                 assignUserIds = List.of(assignUserId);
             } else {
                 return request.getBatchIds().size();
             }
         }

         // 预计算每个用户的剩余库容
         Map<String, Integer> userCapacitiesMap = new HashMap<>();
         for (String targetUserId : assignUserIds) {
             CustomerCapacity customerCapacity = getUserCapacity(targetUserId, currentOrgId);
             int remainingCapacity = Integer.MAX_VALUE;
             if (customerCapacity != null && customerCapacity.getCapacity() != null) {
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
                     excludeCount = extCustomerMapper.countByOwnerAndStages(targetUserId, excludeStageIds);
                 }
                 LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
                 customerWrapper.eq(Customer::getOwner, targetUserId).eq(Customer::getInSharedPool, false);
                 int ownCount = customerMapper.selectListByLambda(customerWrapper).size();
                 remainingCapacity = Math.max(0, customerCapacity.getCapacity() - (ownCount - excludeCount));
             }
             userCapacitiesMap.put(targetUserId, remainingCapacity);
         }

         int totalCustomers = request.getBatchIds().size();
         int assignedCount = 0;
         int userIdx = 0;
         int userCount = assignUserIds.size();

         // 轮询分配
         for (String customerId : request.getBatchIds()) {
             int attempts = 0;
             boolean success = false;
             while (attempts < userCount) {
                 String currentUserId = assignUserIds.get(userIdx);
                 Integer capacity = userCapacitiesMap.get(currentUserId);
                 if (capacity != null && capacity > 0) {
                     ownCustomer(customerId, currentUserId, null, currentUser, LogType.ASSIGN, currentOrgId, false);
                     userCapacitiesMap.put(currentUserId, capacity - 1);
                     assignedCount++;
                     success = true;
                     userIdx = (userIdx + 1) % userCount;
                     break;
                 } else {
                     userIdx = (userIdx + 1) % userCount;
                     attempts++;
                 }
             }
             if (!success) {
                 // 所有选中用户库容均不足，该客户无法分配
             }
         }
         return totalCustomers - assignedCount;
     }

    /**
     * 批量删除客户
     *
     * @param ids 客户ID集合
     */
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

    public int batchDeleteByCondition(CustomerPageRequest request, String userId, String orgId) {
        int deletedCount = 0;
        while (true) {
            PageHelper.startPage(1, BATCH_DELETE_BY_CONDITION_SIZE, false);
            List<String> deleteIds = extCustomerMapper.listIds(request, orgId, userId, null);
            if (CollectionUtils.isEmpty(deleteIds)) {
                return deletedCount;
            }
            batchDelete(deleteIds, userId, orgId);
            deletedCount += deleteIds.size();
        }
    }

    /**
     * 转移客户到指定公海池
     *
     * @param customerId   客户ID
     * @param targetPoolId 目标公海池ID
     * @param currentUser  当前用户ID
     * @param currentOrgId 当前组织ID
     */
    public void transfer(String customerId, String targetPoolId, String currentUser, String currentOrgId) {
        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null) {
            throw new GenericException(Translator.get("customer_not_exist"));
        }
        if (!Boolean.TRUE.equals(customer.getInSharedPool())) {
            throw new GenericException(Translator.get("pool_transfer_only_pool_customer"));
        }
        CustomerPool targetPool = poolMapper.selectByPrimaryKey(targetPoolId);
        if (targetPool == null) {
            throw new GenericException(Translator.get("pool_import_pool_not_exist"));
        }
        if (!targetPool.getEnable()) {
            throw new GenericException(Translator.get("pool_import_pool_disabled"));
        }

        long now = System.currentTimeMillis();
        customer.setPoolId(targetPoolId);
        customer.setOwner(null);
        customer.setCollectionTime(null);
        customer.setStage(null);
        customer.setStageStatus(null);
        customer.setUpdateUser(currentUser);
        customer.setUpdateTime(now);
        extCustomerMapper.updateIncludeNullById(customer);

        logService.add(new LogDTO(currentOrgId, customer.getId(), currentUser, LogType.UPDATE, LogModule.CUSTOMER_POOL,
                Translator.getWithArgs("pool_transfer_log", customer.getName(), targetPool.getName())));
    }

    public boolean preCheck4ClueMoveToPool(String phone, String targetPoolId, String currentOrgId) {
        CustomerPool targetPool = poolMapper.selectByPrimaryKey(targetPoolId);
        if (targetPool == null || !Boolean.TRUE.equals(targetPool.getEnable())) {
            log.warn("线索池自动分发公海池预检查失败：{}", Translator.get("customer_pool_not_exist"));
            return false;
        }

        List<MobileConflictDTO> conflicts = extCustomerMapper.getMobileConflicts(currentOrgId, targetPoolId, List.of(phone));
        boolean match = CollectionUtils.isEmpty(conflicts) ||
                conflicts.stream()
                        .noneMatch(m -> phone.equals(m.getMobile()) && !"NONE".equals(m.getConflictType()));
        if(!match) {
            log.warn("线索池自动分发公海池预检查失败：{}", Translator.get("phone.exist"));
        }
        return match;
    }

    /**
     * 批量预检查线索手机号，返回可分发手机号集合。
     */
    public Set<String> batchPreCheckClueMoveToPool(List<String> phones, String targetPoolId, String currentOrgId) {
        if (CollectionUtils.isEmpty(phones)) {
            return Set.of();
        }
        List<String> validPhones = phones.stream().filter(StringUtils::isNotBlank).distinct().toList();
        if (CollectionUtils.isEmpty(validPhones)) {
            return Set.of();
        }

        CustomerPool targetPool = poolMapper.selectByPrimaryKey(targetPoolId);
        if (targetPool == null || !Boolean.TRUE.equals(targetPool.getEnable())) {
            log.warn("线索池批量分发公海池预检查失败：{}", Translator.get("customer_pool_not_exist"));
            return Set.of();
        }

        List<MobileConflictDTO> conflicts = extCustomerMapper.getMobileConflicts(currentOrgId, targetPoolId, validPhones);
        Set<String> conflictPhones = conflicts.stream()
                .filter(m -> !"NONE".equals(m.getConflictType()))
                .map(MobileConflictDTO::getMobile)
                .collect(Collectors.toSet());
        Set<String> allowedPhones = new HashSet<>(validPhones);
        allowedPhones.removeAll(conflictPhones);
        return allowedPhones;
    }

    /**
     * 批量转移客户到指定公海池
     *
     * @param customerIds  客户ID集合
     * @param targetPoolId 目标公海池ID
     * @param currentUser  当前用户ID
     * @param currentOrgId 当前组织ID
     */
    public void batchTransfer(List<String> customerIds, String targetPoolId, String currentUser, String currentOrgId) {
        CustomerPool targetPool = poolMapper.selectByPrimaryKey(targetPoolId);
        if (targetPool == null) {
            throw new GenericException(Translator.get("pool_import_pool_not_exist"));
        }
        if (!targetPool.getEnable()) {
            throw new GenericException(Translator.get("pool_import_pool_disabled"));
        }

        List<Customer> customers = customerMapper.selectByIds(customerIds);
        for (Customer customer : customers) {
            if (!Boolean.TRUE.equals(customer.getInSharedPool())) {
                throw new GenericException(Translator.get("pool_transfer_only_pool_customer"));
            }
        }

        long now = System.currentTimeMillis();
        for (Customer customer : customers) {
            customer.setPoolId(targetPoolId);
            customer.setOwner(null);
            customer.setCollectionTime(null);
            customer.setStage(null);
            customer.setStageStatus(null);
            customer.setUpdateUser(currentUser);
            customer.setUpdateTime(now);
            extCustomerMapper.updateIncludeNullById(customer);

            logService.add(new LogDTO(currentOrgId, customer.getId(), currentUser, LogType.UPDATE, LogModule.CUSTOMER_POOL,
                    Translator.getWithArgs("pool_transfer_log", customer.getName(), targetPool.getName())));
        }
    }

    /**
     * 校验库容
     *
     * @param processCount 处理数量
     * @param ownUserId    负责人用户ID
     * @param currentOrgId 当前组织ID
     */
    public void validateCapacity(int processCount, String ownUserId, String currentOrgId) {
        // 实际可处理条数 = 负责人库容容量 - 所领取的数量 < 处理数量, 提示库容不足.
        // 硬编码：排除"回款"和"无效客户"阶段的客户
        CustomerCapacity customerCapacity = getUserCapacity(ownUserId, currentOrgId);
        if (customerCapacity == null || customerCapacity.getCapacity() == null) {
            return;
        }
        // 获取需要排除的阶段ID列表
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
            excludeCount = extCustomerMapper.countByOwnerAndStages(ownUserId, excludeStageIds);
        }
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.eq(Customer::getOwner, ownUserId).eq(Customer::getInSharedPool, false);
        int ownCount = customerMapper.selectListByLambda(customerWrapper).size();
        if (customerCapacity.getCapacity() - (ownCount - excludeCount) < processCount) {
            throw new GenericException(Translator.getWithArgs("customer.capacity.over", Math.max(customerCapacity.getCapacity() - ownCount, 0)));
        }
    }

    /**
     * 校验每日领取数量
     *
     * @param pickingCount 领取数量
     * @param ownUserId    负责人用户ID
     * @param pickRule     领取规则
     */
    public void validateDailyPickNum(int pickingCount, String ownUserId, CustomerPoolPickRule pickRule) {
        if (pickRule.getLimitOnNumber()) {
            LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
            customerWrapper
                    .eq(Customer::getOwner, ownUserId)
                    .eq(Customer::getInSharedPool, false)
                    .between(Customer::getCollectionTime, TimeUtils.getTodayStart(), TimeUtils.getTodayStart() + DAY_MILLIS);
            List<Customer> customers = customerMapper.selectListByLambda(customerWrapper);
            int pickedCount = customers.size();
            if (pickingCount + pickedCount > pickRule.getPickNumber()) {
                throw new GenericException(Translator.get("customer.daily.pick.over"));
            }
        }
    }

    /**
     * 获取用户库容
     *
     * @param userId         用户ID
     * @param organizationId 组织ID
     *
     * @return 库容
     */
    public CustomerCapacity getUserCapacity(String userId, String organizationId) {
        List<String> scopeIds = userExtendService.getUserScopeIds(userId, organizationId);
        return extCustomerCapacityMapper.getCapacityByScopeIds(scopeIds, organizationId);
    }

    /**
     * 拥有客户
     *
     * @param customerId 客户ID
     * @param ownerId    拥有人ID
     */
    private void ownCustomer(String customerId, String ownerId, CustomerPoolPickRule pickRule,
                             String operateUserId, String logType, String currentOrgId, boolean isPoolAdmin) {

        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null) {
            throw new IllegalArgumentException(Translator.get("customer.not.exist"));
        }

        if (!isPoolAdmin && pickRule != null) {
            if (pickRule.getLimitNew()) {
                LocalDateTime joinPoolTime = Instant.ofEpochMilli(customer.getUpdateTime())
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                LocalDateTime releaseDate = joinPoolTime.plusDays(pickRule.getNewPickInterval());
                if (releaseDate.isAfter(LocalDateTime.now())) {
                    throw new GenericException(Translator.getWithArgs(
                            "pool.data.release.date",
                            releaseDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    ));
                }
            }

            if (pickRule.getLimitPreOwner()) {
                List<CustomerOwner> customerOwners = ownerMapper.selectListByLambda(
                        new LambdaQueryWrapper<CustomerOwner>().eq(CustomerOwner::getCustomerId, customerId)
                );
                if (CollectionUtils.isNotEmpty(customerOwners)) {
                    CustomerOwner lastOwner = customerOwners.stream()
                            .max(Comparator.comparingLong(CustomerOwner::getCollectionTime))
                            .orElse(null);
                    if (lastOwner != null && Strings.CS.equals(lastOwner.getOwner(), ownerId)) {
                        long nextPickMillis = lastOwner.getEndTime()
                                + pickRule.getPickIntervalDays() * DAY_MILLIS;
                        if (System.currentTimeMillis() < nextPickMillis) {
                            LocalDateTime nextPickTime = Instant.ofEpochMilli(nextPickMillis)
                                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
                            throw new GenericException(Translator.getWithArgs(
                                    "customer.pre_owner.pick.limit",
                                    nextPickTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            ));
                        }
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        customer.setPoolId(null);
        customer.setInSharedPool(false);
        customer.setOwner(ownerId);
        customer.setCollectionTime(now);
        customer.setReasonId(null);
        customer.setFollower(null);
        customer.setFollowTime(null);
        customer.setUpdateUser(ownerId);
        customer.setUpdateTime(now);
        List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(currentOrgId);
        if (CollectionUtils.isNotEmpty(stageConfigList)) {
            customer.setStage(stageConfigList.getFirst().getId());
            customer.setStageStatus(CustomerStageService.STATUS_NEW);
        }
        extCustomerMapper.updateIncludeNullById(customer);

        // 清空负责人历史记录
        customerOwnerHistoryService.deleteByCustomerIds(List.of(customerId));

        // 客户从公海进入私海时不再自动创建跟进计划（需求：2026-04-10）
        // followUpPlanService.createInitialStageFollowPlanForCustomer(customerId, ownerId, currentOrgId);

        // 只更新最近一次销售负责人的联系人（联系人为空的）
        String recentOwner = extCustomerOwnerMapper.getRecentOwner(customerId);
        customerContactService.updatePoolContactOwner(customerId, ownerId, recentOwner, currentOrgId);

        logService.add(new LogDTO(currentOrgId, customer.getId(), operateUserId, logType,
                LogModule.CUSTOMER_POOL, customer.getName()));

        if (Strings.CS.equals(logType, LogType.ASSIGN)) {
            commonNoticeSendService.sendNotice(
                    NotificationConstants.Module.CUSTOMER,
                    NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED,
                    customer.getName(), operateUserId, currentOrgId,
                    List.of(ownerId), true
            );
        }
    }

    public void batchUpdate(ResourceBatchEditRequest request, String userId, String organizationId) {
        BaseField field = customerFieldService.getAndCheckField(request.getFieldId(), organizationId);

        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_OWNER.getBusinessKey())) {
            // 修改负责人，走批量分配的接口
            PoolBatchAssignRequest batchAssignRequest = new PoolBatchAssignRequest();
            batchAssignRequest.setBatchIds(request.getIds());
            batchAssignRequest.setAssignUserId(request.getFieldValue().toString());
            batchAssign(batchAssignRequest, batchAssignRequest.getAssignUserId(), organizationId, userId);
            return;
        }

        List<Customer> originCustomers = customerMapper.selectByIds(request.getIds());

        customerFieldService.batchUpdate(request, field, originCustomers, Customer.class, LogModule.CUSTOMER_POOL, extCustomerMapper::batchUpdate, userId, organizationId);
    }

    public List<ChartResult> chart(PoolCustomerChartAnalysisRequest request, String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        ModuleFormConfigDTO formConfig = Objects.requireNonNull(CommonBeanFactory.getBean(CustomerService.class)).getFormConfig(orgId);
        formConfig.getFields().addAll(BaseChartService.getChartBaseFields());
        ChartAnalysisDbRequest chartAnalysisDbRequest = ConditionFilterUtils.parseChartAnalysisRequest(request, formConfig);
        CustomerChartAnalysisDbRequest customerChartAnalysisDbRequest = BeanUtils.copyBean(new CustomerChartAnalysisDbRequest(), chartAnalysisDbRequest);
        customerChartAnalysisDbRequest.setPoolId(request.getPoolId());
        List<ChartResult> chartResults = extCustomerMapper.chart(customerChartAnalysisDbRequest, userId, orgId, deptDataPermission);
        return baseChartService.translateAxisName(formConfig, chartAnalysisDbRequest, chartResults);
    }
}
