package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.dto.BatchUpdateDbParam;
import cn.cordys.common.dto.ChartAnalysisDbRequest;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.chart.ChartResult;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.service.BaseChartService;
import cn.cordys.common.util.*;
import cn.cordys.context.TenantContext;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.*;
import cn.cordys.crm.customer.dto.CustomerPoolDTO;
import cn.cordys.crm.customer.dto.CustomerPoolPickRuleDTO;
import cn.cordys.crm.customer.dto.CustomerPoolRecycleRuleDTO;
import cn.cordys.crm.customer.dto.MobileConflictDTO;
import cn.cordys.crm.customer.dto.request.CustomerChartAnalysisDbRequest;
import cn.cordys.crm.customer.dto.request.CustomerPageRequest;
import cn.cordys.crm.customer.dto.request.PoolBatchAssignByConditionRequest;
import cn.cordys.crm.customer.dto.request.PoolBatchPickByConditionRequest;
import cn.cordys.crm.customer.dto.request.PoolBatchTransferRequest;
import cn.cordys.crm.customer.dto.request.PoolBatchTransferByConditionRequest;
import cn.cordys.crm.customer.dto.request.PoolBatchUpdateByConditionRequest;
import cn.cordys.crm.customer.dto.request.PoolCustomerChartAnalysisRequest;
import cn.cordys.crm.customer.dto.request.PoolCustomerPickRequest;
import cn.cordys.crm.customer.mapper.ExtCustomerCapacityMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerOwnerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.follow.service.FollowUpPlanService;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.dto.MessageDetailDTO;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.FilterConditionDTO;
import cn.cordys.crm.system.dto.RuleConditionDTO;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.request.PoolBatchAssignRequest;
import cn.cordys.crm.system.dto.request.PoolBatchPickRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.notice.common.NoticeModel;
import cn.cordys.crm.system.notice.common.Receiver;
import cn.cordys.crm.system.notice.sender.insite.InSiteNoticeSender;
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
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PoolCustomerService {

    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000;
    private static final int BATCH_DELETE_BY_CONDITION_SIZE = 2000;
    private static final int BATCH_PICK_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_ASSIGN_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_TRANSFER_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_UPDATE_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_ASSIGN_UPDATE_SIZE = 200;
    private static final String BATCH_POOL_OPERATION_LOCK_PREFIX = "crm:pool:batch-op:";
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
    private InSiteNoticeSender inSiteNoticeSender;
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
    @Resource
    private CustomerWechatFriendStatusService customerWechatFriendStatusService;
    @Resource
    private CustomerMobileRuleService customerMobileRuleService;
    @Resource(name = "threadPoolTaskExecutor")
    private Executor executor;
    @Resource
    private Redisson redisson;
    @Resource
    private TransactionTemplate transactionTemplate;

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
        Customer customer = customerMapper.selectByPrimaryKey(request.getCustomerId());
        validateOwnerMobileConflict(customer, currentUser, currentOrgId, null, null);
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
        Customer customer = customerMapper.selectByPrimaryKey(id);
        validateOwnerMobileConflict(customer, assignUserId, currentOrgId, null, null);
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
        validateBatchPickMobileConflict(request.getBatchIds(), currentUser, currentOrgId);
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
         Map<String, Set<String>> userOwnedPoolMobileMap = new HashMap<>();
         Map<String, Set<String>> userPrivateConflictMobileMap = new HashMap<>();
         Map<String, Customer> customerMap = customerMapper.selectByIds(request.getBatchIds()).stream()
                 .collect(Collectors.toMap(Customer::getId, customer -> customer));
         List<String> candidateMobiles = customerMap.values().stream().map(Customer::getMobile).toList();
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
            userOwnedPoolMobileMap.put(targetUserId, customerMobileRuleService.loadOwnerPoolMobiles(targetUserId, currentOrgId));
            userPrivateConflictMobileMap.put(targetUserId,
                     customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(candidateMobiles, targetUserId, currentOrgId));
         }

         int totalCustomers = request.getBatchIds().size();
         int assignedCount = 0;
         int userIdx = 0;
         int userCount = assignUserIds.size();

         // 轮询分配
         for (String customerId : request.getBatchIds()) {
             Customer customer = customerMap.get(customerId);
             if (customer == null) {
                 continue;
             }
             int attempts = 0;
             boolean success = false;
             while (attempts < userCount) {
                String currentUserId = assignUserIds.get(userIdx);
                Integer capacity = userCapacitiesMap.get(currentUserId);
                Set<String> ownedPoolMobiles = userOwnedPoolMobileMap.computeIfAbsent(currentUserId,
                        key -> customerMobileRuleService.loadOwnerPoolMobiles(currentUserId, currentOrgId));
                Set<String> privateConflictMobiles = userPrivateConflictMobileMap.computeIfAbsent(currentUserId,
                        key -> customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(candidateMobiles, currentUserId, currentOrgId));
                 if (capacity != null && capacity > 0
                         && !customerMobileRuleService.hasPoolImportReceiveConflict(customer == null ? null : customer.getMobile(),
                         privateConflictMobiles, ownedPoolMobiles)) {
                     ownCustomer(customerId, currentUserId, null, currentUser, LogType.ASSIGN, currentOrgId, false);
                     userCapacitiesMap.put(currentUserId, capacity - 1);
                     customerMobileRuleService.addOwnedMobile(customer, ownedPoolMobiles);
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

    public Map<String, Object> batchDeleteByCondition(CustomerPageRequest request, String userId, String orgId) {
        if (StringUtils.isBlank(request.getPoolId())) {
            throw new GenericException(Translator.get("common.param.error"));
        }

        PageHelper.startPage(1, 1, false);
        List<String> previewIds = extCustomerMapper.listIds(request, orgId, userId, null);
        if (CollectionUtils.isEmpty(previewIds)) {
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "未查询到可删除客户"
            );
        }

        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + request.getPoolId());
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException("按筛选删除加锁失败");
        }
        if (!locked) {
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量任务执行中，请稍后再试"
            );
        }

        CustomerPageRequest asyncRequest = BeanUtils.copyBean(new CustomerPageRequest(), request);
        try {
            executor.execute(() -> doBatchDeleteByConditionAsync(taskId, lockThreadId, asyncRequest, userId, orgId, lock));
        } catch (Exception ex) {
            releasePoolBatchLock(lock, lockKey, lockThreadId);
            throw ex;
        }

        log.info("[POOL_BATCH_DELETE_SUBMIT] taskId={}, poolId={}, operator={}",
                taskId, request.getPoolId(), userId);
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", "批量删除任务已提交"
        );
    }

    /**
     * 按筛选条件批量领取客户（取当前筛选排序下的前 pickCount 条）。
     */
    public Map<String, Object> batchPickByCondition(PoolBatchPickByConditionRequest request, String currentUser, String currentOrgId) {
        int pickCount = Math.min(request.getPickCount(), BATCH_PICK_BY_CONDITION_MAX_SIZE);
        PageHelper.startPage(1, pickCount, false);
        List<String> ids = extCustomerMapper.listIds(request, currentOrgId, currentUser, null);
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "未查询到可领取客户"
            );
        }
        PoolBatchPickRequest batchPickRequest = new PoolBatchPickRequest();
        batchPickRequest.setBatchIds(ids);
        batchPickRequest.setPoolId(request.getPoolId());
        return batchPick_new(batchPickRequest, currentUser, currentOrgId);
    }

    /**
     * 按筛选条件批量分配客户。
     * 这里保留原有“先按筛选条件取前 N 条客户”的语义，
     * 但真正执行时改走新的异步批量分配链路。
     *
     * @return 任务受理结果，accepted=true 表示已提交异步任务
     */
    public Map<String, Object> batchAssignByCondition(PoolBatchAssignByConditionRequest request, String currentOrgId, String currentUser) {
        int assignCount = Math.min(request.getAssignCount(), BATCH_ASSIGN_BY_CONDITION_MAX_SIZE);
        PageHelper.startPage(1, assignCount, false);
        List<String> ids = extCustomerMapper.listIds(request, currentOrgId, currentUser, null);
        if (CollectionUtils.isEmpty(ids)) {
            Map<String, Object> result = new HashMap<>(4);
            result.put("accepted", false);
            result.put("taskId", null);
            result.put("message", "未查询到可分配客户");
            return result;
        }
        PoolBatchAssignRequest batchAssignRequest = new PoolBatchAssignRequest();
        batchAssignRequest.setBatchIds(ids);
        batchAssignRequest.setAssignUserId(request.getAssignUserId());
        batchAssignRequest.setAssignUserIds(request.getAssignUserIds());
        return batchAssign_new(batchAssignRequest, request.getAssignUserId(), currentOrgId, currentUser);
    }

    /**
     * 按筛选条件批量转移客户（取当前筛选排序下的前 transferCount 条）。
     */
    public Map<String, Object> batchTransferByCondition(PoolBatchTransferByConditionRequest request, String currentUser, String currentOrgId) {
        int transferCount = Math.min(request.getTransferCount(), BATCH_TRANSFER_BY_CONDITION_MAX_SIZE);
        PageHelper.startPage(1, transferCount, false);
        List<String> ids = extCustomerMapper.listIds(request, currentOrgId, currentUser, null);
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "未查询到可转移客户"
            );
        }
        PoolBatchTransferRequest batchTransferRequest = new PoolBatchTransferRequest();
        batchTransferRequest.setBatchIds(ids);
        batchTransferRequest.setTargetPoolId(request.getTargetPoolId());
        return batchTransfer_new(batchTransferRequest, currentUser, currentOrgId);
    }

    /**
     * 按筛选条件批量编辑客户（取当前筛选排序下的前 updateCount 条）。
     */
    public Map<String, Object> batchUpdateByCondition(PoolBatchUpdateByConditionRequest request, String currentUser, String currentOrgId) {
        CustomerPool pool = poolMapper.selectByPrimaryKey(request.getPoolId());
        if (pool == null) {
            throw new GenericException(Translator.get("customer_pool_not_exist"));
        }

        BaseField field = customerFieldService.getAndCheckField(request.getFieldId(), currentOrgId);

        int updateCount = Math.min(request.getUpdateCount(), BATCH_UPDATE_BY_CONDITION_MAX_SIZE);
        PageHelper.startPage(1, updateCount, false);
        List<String> ids = extCustomerMapper.listIds(request, currentOrgId, currentUser, null);
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "未查询到可编辑客户"
            );
        }
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey()) && ids.size() > 1) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }
        if (field.needRepeatCheck() && ids.size() > 1 && request.getFieldValue() != null
                && StringUtils.isNotBlank(String.valueOf(request.getFieldValue()))) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }

        ResourceBatchEditRequest batchEditRequest = new ResourceBatchEditRequest();
        batchEditRequest.setIds(ids);
        batchEditRequest.setFieldId(request.getFieldId());
        batchEditRequest.setFieldValue(request.getFieldValue());
        return batchUpdate_new(batchEditRequest, pool, currentUser, currentOrgId);
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
        customerWechatFriendStatusService.recalculateCustomer(customerId, ownerId);

        // 清空负责人历史记录
        customerOwnerHistoryService.deleteByCustomerIds(List.of(customerId));

        // 客户从公海进入私海时不再自动创建跟进计划（需求：2026-04-10）
        // followUpPlanService.createInitialStageFollowPlanForCustomer(customerId, ownerId, currentOrgId);

        // 只更新最近一次销售负责人的联系人（联系人为空的）
        String recentOwner = extCustomerOwnerMapper.getRecentOwner(customerId);
        customerContactService.updatePoolContactOwner(customerId, ownerId, recentOwner, currentOrgId);

        logService.add(new LogDTO(currentOrgId, customer.getId(), operateUserId, logType,
                LogModule.CUSTOMER_POOL, customer.getName()));

//        if (Strings.CS.equals(logType, LogType.ASSIGN)) {
//            commonNoticeSendService.sendNotice(
//                    NotificationConstants.Module.CUSTOMER,
//                    NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED,
//                    customer.getName(), operateUserId, currentOrgId,
//                    List.of(ownerId), true
//            );
//        }
    }

    private void validateBatchPickMobileConflict(List<String> customerIds, String ownerId, String currentOrgId) {
        Map<String, Customer> customerMap = customerMapper.selectByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, customer -> customer));
        List<String> mobiles = customerMap.values().stream().map(Customer::getMobile).toList();
        Set<String> ownerPrivateConflictMobiles = customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(mobiles, ownerId, currentOrgId);
        Set<String> ownedPoolMobiles = customerMobileRuleService.loadOwnerPoolMobiles(ownerId, currentOrgId);
        for (String customerId : customerIds) {
            Customer customer = customerMap.get(customerId);
            validateOwnerMobileConflict(customer, ownerId, currentOrgId, ownerPrivateConflictMobiles, ownedPoolMobiles);
            customerMobileRuleService.addOwnedMobile(customer, ownedPoolMobiles);
        }
    }

    private void validateOwnerMobileConflict(Customer customer, String ownerId, String currentOrgId,
                                             Set<String> ownerPrivateConflictMobiles, Set<String> ownedPoolMobiles) {
        String mobile = customer == null ? null : StringUtils.trimToNull(customer.getMobile());
        if (mobile == null) {
            return;
        }
        Set<String> privateConflictMobiles = ownerPrivateConflictMobiles != null
                ? ownerPrivateConflictMobiles
                : customerMobileRuleService.findPoolImportReceiveBlockingOwnerPrivateMobiles(List.of(mobile), ownerId, currentOrgId);
        Set<String> poolMobiles = ownedPoolMobiles != null
                ? ownedPoolMobiles
                : customerMobileRuleService.loadOwnerPoolMobiles(ownerId, currentOrgId);
        if (customerMobileRuleService.hasPoolImportReceiveConflict(mobile, privateConflictMobiles, poolMobiles)) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
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
        executePoolBatchUpdate(request, originCustomers, field, userId, organizationId);
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

    /**
     * 批量领取新链路：
     * 1. 同步请求阶段只做参数校验、手机号重复规则预校验、同池互斥锁、异步任务提交
     * 2. 真正的领取逻辑放到异步线程执行，避免页面长时间阻塞
     */
    public Map<String, Object> batchPick_new(PoolBatchPickRequest request, String currentUser, String currentOrgId) {
        if (CollectionUtils.isEmpty(request.getBatchIds()) || StringUtils.isBlank(request.getPoolId())) {
            throw new GenericException(Translator.get("common.param.error"));
        }

        List<Customer> selectedCustomers = customerMapper.selectByIds(request.getBatchIds());
        String poolId = resolvePoolIdForBatchPick(selectedCustomers, request.getPoolId());
        CustomerPool pool = poolMapper.selectByPrimaryKey(poolId);
        if (pool == null) {
            throw new GenericException(Translator.get("customer_pool_not_exist"));
        }

        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + poolId);
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException("批量领取加锁失败");
        }
        if (!locked) {
            log.info("[POOL_BATCH_PICK_LOCK_REJECTED] poolId={}, operator={}, lockKey={}",
                    poolId, currentUser, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量任务执行中，请稍后再试"
            );
        }

        PoolBatchPickRequest asyncRequest = new PoolBatchPickRequest();
        asyncRequest.setBatchIds(new ArrayList<>(request.getBatchIds()));
        asyncRequest.setPoolId(poolId);
        try {
            executor.execute(() -> doBatchPickAsync(taskId, lockThreadId, pool, asyncRequest, currentUser, currentOrgId, lock));
        } catch (Exception ex) {
            releasePoolBatchLock(lock, lockKey, lockThreadId);
            throw ex;
        }

        log.info("[POOL_BATCH_PICK_SUBMIT] taskId={}, poolId={}, operator={}, batchSize={}",
                taskId, poolId, currentUser, request.getBatchIds().size());
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", "批量领取任务已提交"
        );
    }

    /**
     * 批量分配新链路：
     * 1. 同步请求阶段只做参数校验、同池互斥锁、异步任务提交
     * 2. 真正的分配逻辑放到异步线程执行，避免页面长时间阻塞
     *
     * @param request      批量分配请求
     * @param assignUserId 兼容旧接口的单个分配用户ID
     * @param currentOrgId 当前组织ID
     * @param currentUser  当前操作人
     * @return 仅返回是否受理、任务ID、提示文案
     */
    public Map<String, Object> batchAssign_new(PoolBatchAssignRequest request, String assignUserId, String currentOrgId, String currentUser) {
        List<String> assignUserIds = resolveAssignUserIds(request, assignUserId);
        if (CollectionUtils.isEmpty(assignUserIds)) {
            throw new GenericException(Translator.get("user.not.exist"));
        }
        if (CollectionUtils.isEmpty(request.getBatchIds())) {
            throw new GenericException(Translator.get("common.param.error"));
        }

        List<Customer> selectedCustomers = customerMapper.selectByIds(request.getBatchIds());
        String poolId = resolvePoolIdForBatchAssign(selectedCustomers);
        CustomerPool pool = poolMapper.selectByPrimaryKey(poolId);
        if (pool == null) {
            throw new GenericException(Translator.get("customer_pool_not_exist"));
        }

        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + poolId);
        RLock lock = redisson.getLock(lockKey);
        // 同一个公海池同一时间只允许一个批量分配任务执行
        boolean locked;
        try {
            // leaseTime 传 -1 时使用 Redisson watchdog 自动续期，threadId 用 taskId 透传给异步执行线程解锁。
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException("批量分配加锁失败");
        }
        if (!locked) {
            log.info("[POOL_BATCH_ASSIGN_LOCK_REJECTED] poolId={}, operator={}, lockKey={}",
                    poolId, currentUser, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量分配任务执行中，请稍后再试"
            );
        }

        PoolBatchAssignRequest asyncRequest = new PoolBatchAssignRequest();
        asyncRequest.setBatchIds(new ArrayList<>(request.getBatchIds()));
        asyncRequest.setAssignUserId(assignUserId);
        asyncRequest.setAssignUserIds(new ArrayList<>(assignUserIds));

        try {
            // 请求线程只负责提交任务，不在这里做重计算和批量落库
            executor.execute(() -> doBatchAssignAsync(taskId, lockThreadId, pool, asyncRequest, currentOrgId, currentUser, lock));
        } catch (Exception ex) {
            releasePoolBatchLock(lock, lockKey, lockThreadId);
            throw ex;
        }

        log.info("[POOL_BATCH_ASSIGN_SUBMIT] taskId={}, poolId={}, operator={}, batchSize={}, assignUserCount={}",
                taskId, poolId, currentUser, request.getBatchIds().size(), assignUserIds.size());

        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", "批量分配任务已提交"
        );
    }

    /**
     * 批量转移新链路：
     * 1. 同步请求阶段只做参数校验、同池互斥锁、异步任务提交
     * 2. 真正的转移逻辑放到异步线程执行，避免页面长时间阻塞
     */
    public Map<String, Object> batchTransfer_new(PoolBatchTransferRequest request, String currentUser, String currentOrgId) {
        if (CollectionUtils.isEmpty(request.getBatchIds()) || StringUtils.isBlank(request.getTargetPoolId())) {
            throw new GenericException(Translator.get("common.param.error"));
        }

        List<Customer> selectedCustomers = customerMapper.selectByIds(request.getBatchIds());
        String sourcePoolId = resolveSourcePoolIdForBatchTransfer(selectedCustomers);
        CustomerPool sourcePool = poolMapper.selectByPrimaryKey(sourcePoolId);
        if (sourcePool == null) {
            throw new GenericException(Translator.get("customer_pool_not_exist"));
        }
        CustomerPool targetPool = poolMapper.selectByPrimaryKey(request.getTargetPoolId());
        if (targetPool == null) {
            throw new GenericException(Translator.get("pool_import_pool_not_exist"));
        }
        if (!Boolean.TRUE.equals(targetPool.getEnable())) {
            throw new GenericException(Translator.get("pool_import_pool_disabled"));
        }

        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + sourcePoolId);
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            // leaseTime 传 -1 时使用 Redisson watchdog 自动续期，threadId 用 taskId 透传给异步执行线程解锁。
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException("批量转移加锁失败");
        }
        if (!locked) {
            log.info("[POOL_BATCH_TRANSFER_LOCK_REJECTED] sourcePoolId={}, operator={}, lockKey={}",
                    sourcePoolId, currentUser, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量转移任务执行中，请稍后再试"
            );
        }

        PoolBatchTransferRequest asyncRequest = new PoolBatchTransferRequest();
        asyncRequest.setBatchIds(new ArrayList<>(request.getBatchIds()));
        asyncRequest.setTargetPoolId(request.getTargetPoolId());
        try {
            executor.execute(() -> doBatchTransferAsync(taskId, lockThreadId, sourcePool, targetPool, asyncRequest,
                    currentUser, currentOrgId, lock));
        } catch (Exception ex) {
            releasePoolBatchLock(lock, lockKey, lockThreadId);
            throw ex;
        }

        log.info("[POOL_BATCH_TRANSFER_SUBMIT] taskId={}, sourcePoolId={}, targetPoolId={}, operator={}, batchSize={}",
                taskId, sourcePoolId, request.getTargetPoolId(), currentUser, request.getBatchIds().size());
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", "批量转移任务已提交"
        );
    }

    /**
     * 批量编辑新链路：
     * 1. 同步请求阶段只做参数校验、同池互斥锁、异步任务提交
     * 2. 真正的编辑逻辑放到异步线程执行，避免页面长时间阻塞
     */
    public Map<String, Object> batchUpdate_new(ResourceBatchEditRequest request, CustomerPool pool, String currentUser, String currentOrgId) {
        if (CollectionUtils.isEmpty(request.getIds()) || StringUtils.isBlank(request.getFieldId())) {
            throw new GenericException(Translator.get("common.param.error"));
        }

        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + pool.getId());
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException("批量编辑加锁失败");
        }
        if (!locked) {
            log.info("[POOL_BATCH_UPDATE_LOCK_REJECTED] poolId={}, operator={}, lockKey={}",
                    pool.getId(), currentUser, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量编辑任务执行中，请稍后再试"
            );
        }

        ResourceBatchEditRequest asyncRequest = new ResourceBatchEditRequest();
        asyncRequest.setIds(new ArrayList<>(request.getIds()));
        asyncRequest.setFieldId(request.getFieldId());
        asyncRequest.setFieldValue(request.getFieldValue());
        try {
            executor.execute(() -> doBatchUpdateAsync(taskId, lockThreadId, pool, asyncRequest, currentUser, currentOrgId, lock));
        } catch (Exception ex) {
            releasePoolBatchLock(lock, lockKey, lockThreadId);
            throw ex;
        }

        log.info("[POOL_BATCH_UPDATE_SUBMIT] taskId={}, poolId={}, operator={}, batchSize={}, fieldId={}",
                taskId, pool.getId(), currentUser, request.getIds().size(), request.getFieldId());
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", "批量编辑任务已提交"
        );
    }

    /**
     * 异步执行批量领取主流程：
     * 1. 重新过滤当前仍在目标公海池中的客户
     * 2. 在内存中按顺序应用手机号重复规则、领取规则、库容和每日领取限制
     * 3. 按最终领取方案批量落库并补充联系人、状态和日志
     */
    private void doBatchPickAsync(String taskId, long lockThreadId, CustomerPool pool, PoolBatchPickRequest request,
                                  String currentUser, String currentOrgId, RLock lock) {
        long totalStart = System.currentTimeMillis();
        try {
            log.info("[POOL_BATCH_PICK_START] taskId={}, poolId={}, operator={}, batchSize={}",
                    taskId, pool.getId(), currentUser, request.getBatchIds().size());

            long loadStart = System.currentTimeMillis();
            Map<String, Integer> requestOrderMap = new HashMap<>();
            for (int index = 0; index < request.getBatchIds().size(); index++) {
                requestOrderMap.put(request.getBatchIds().get(index), index);
            }
            List<Customer> customers = customerMapper.selectByIds(request.getBatchIds());
            List<Customer> candidates = customers.stream()
                    .filter(Objects::nonNull)
                    .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                    .filter(customer -> Strings.CS.equals(pool.getId(), customer.getPoolId()))
                    .sorted(Comparator.comparingInt(customer -> requestOrderMap.getOrDefault(customer.getId(), Integer.MAX_VALUE)))
                    .toList();
            Map<String, String> recentOwnerMap = buildRecentOwnerMap(candidates);
            List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(currentOrgId);
            String defaultStage = CollectionUtils.isNotEmpty(stageConfigList) ? stageConfigList.getFirst().getId() : null;
            String defaultStageStatus = CollectionUtils.isNotEmpty(stageConfigList) ? CustomerStageService.STATUS_NEW : null;
            CustomerPoolPickRule pickRule = loadPoolPickRule(pool.getId());
            boolean poolAdmin = userExtendService.isPoolAdmin(JSON.parseArray(pool.getOwnerId(), String.class), currentUser, currentOrgId);
            BatchPickPreparedData preparedData = prepareBatchPickData(candidates, currentUser, currentOrgId, pickRule, poolAdmin);
            log.info("[POOL_BATCH_PICK_LOAD_COST] taskId={}, poolId={}, costMs={}, candidateSize={}",
                    taskId, pool.getId(), System.currentTimeMillis() - loadStart, candidates.size());

            long planStart = System.currentTimeMillis();
            BatchPickPlan plan = buildBatchPickPlan(candidates, currentUser, pickRule, poolAdmin, preparedData);
            log.info("[POOL_BATCH_PICK_PLAN_COST] taskId={}, poolId={}, costMs={}, pickedSize={}, skippedSize={}",
                    taskId, pool.getId(), System.currentTimeMillis() - planStart,
                    plan.pickedCustomers().size(), plan.skippedCustomerIds().size());

            if (CollectionUtils.isNotEmpty(plan.pickedCustomers())) {
                transactionTemplate.executeWithoutResult(status ->
                        executeBatchPickPlan(taskId, pool, plan, recentOwnerMap, currentUser, currentOrgId, defaultStage, defaultStageStatus));
            }

            log.info("[POOL_BATCH_PICK_FINISH] taskId={}, poolId={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, pool.getId(), plan.pickedCustomers().size(), plan.skippedCustomerIds().size(),
                    System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            log.error("[POOL_BATCH_PICK_FAILED] taskId={}, poolId={}, operator={}",
                    taskId, pool.getId(), currentUser, ex);
        } finally {
            releasePoolBatchLock(lock, lock.getName(), lockThreadId);
        }
    }

    /**
     * 异步执行按筛选条件批量删除：
     * 1. 保留当前“先按筛选条件取一批 ID，再走现有删除链路”的语义
     * 2. 同步请求阶段只负责提交任务，不阻塞页面
     */
    private void doBatchDeleteByConditionAsync(String taskId, long lockThreadId, CustomerPageRequest request,
                                               String userId, String orgId, RLock lock) {
        long totalStart = System.currentTimeMillis();
        int deletedCount = 0;
        try {
            log.info("[POOL_BATCH_DELETE_START] taskId={}, poolId={}, operator={}",
                    taskId, request.getPoolId(), userId);

            long collectStart = System.currentTimeMillis();
            List<String> matchedIds = collectDeleteIdsByCondition(request, userId, orgId);
            log.info("[POOL_BATCH_DELETE_COLLECT_COST] taskId={}, poolId={}, costMs={}, candidateSize={}",
                    taskId, request.getPoolId(), System.currentTimeMillis() - collectStart, matchedIds.size());

            for (int start = 0; start < matchedIds.size(); start += BATCH_DELETE_BY_CONDITION_SIZE) {
                int end = Math.min(start + BATCH_DELETE_BY_CONDITION_SIZE, matchedIds.size());
                List<String> currentBatchIds = new ArrayList<>(matchedIds.subList(start, end));
                batchDelete(currentBatchIds, userId, orgId);
                deletedCount += currentBatchIds.size();
            }

            log.info("[POOL_BATCH_DELETE_FINISH] taskId={}, poolId={}, successCount={}, totalCostMs={}",
                    taskId, request.getPoolId(), deletedCount, System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            log.error("[POOL_BATCH_DELETE_FAILED] taskId={}, poolId={}, operator={}, deletedCount={}",
                    taskId, request.getPoolId(), userId, deletedCount, ex);
        } finally {
            releasePoolBatchLock(lock, lock.getName(), lockThreadId);
        }
    }

    private List<String> collectDeleteIdsByCondition(CustomerPageRequest request, String userId, String orgId) {
        List<String> matchedIds = new ArrayList<>();
        int pageNum = 1;
        PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_SIZE, false);
        List<String> pageIds = extCustomerMapper.listIds(request, orgId, userId, null);
        while (CollectionUtils.isNotEmpty(pageIds)) {
            matchedIds.addAll(pageIds);
            if (pageIds.size() < BATCH_DELETE_BY_CONDITION_SIZE) {
                return matchedIds;
            }
            pageNum++;
            PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_SIZE, false);
            pageIds = extCustomerMapper.listIds(request, orgId, userId, null);
        }
        return matchedIds;
    }

    /**
     * 异步执行批量分配主流程：
     * 1. 一次性加载批量分配需要的基础数据
     * 2. 在内存中计算每个客户最终应该分给谁
     * 3. 按分配结果批量落库
     * 4. 按用户聚合发送通知
     */
    private void doBatchAssignAsync(String taskId, long lockThreadId, CustomerPool pool, PoolBatchAssignRequest request,
                                    String currentOrgId, String currentUser, RLock lock) {
        long totalStart = System.currentTimeMillis();
        List<String> assignUserIds = resolveAssignUserIds(request, request.getAssignUserId());
        try {
            log.info("[POOL_BATCH_ASSIGN_START] taskId={}, poolId={}, operator={}, batchSize={}, assignUserCount={}",
                    taskId, pool.getId(), currentUser, request.getBatchIds().size(), assignUserIds.size());

            long loadStart = System.currentTimeMillis();
            Map<String, Integer> requestOrderMap = new HashMap<>();
            for (int index = 0; index < request.getBatchIds().size(); index++) {
                requestOrderMap.put(request.getBatchIds().get(index), index);
            }
            List<Customer> customers = customerMapper.selectByIds(request.getBatchIds());
            // 候选客户必须仍然在当前公海池里，并按前端选中的顺序参与轮询分配
            List<Customer> candidates = customers.stream()
                    .filter(Objects::nonNull)
                    .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                    .filter(customer -> Strings.CS.equals(pool.getId(), customer.getPoolId()))
                    .sorted(Comparator.comparingInt(customer -> requestOrderMap.getOrDefault(customer.getId(), Integer.MAX_VALUE)))
                    .toList();
            List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(currentOrgId);
            String defaultStage = CollectionUtils.isNotEmpty(stageConfigList) ? stageConfigList.getFirst().getId() : null;
            String defaultStageStatus = CollectionUtils.isNotEmpty(stageConfigList) ? CustomerStageService.STATUS_NEW : null;
            Map<String, String> recentOwnerMap = buildRecentOwnerMap(candidates);
            BatchAssignPreparedData preparedData = prepareBatchAssignData(assignUserIds, candidates, currentOrgId);
            log.info("[POOL_BATCH_ASSIGN_LOAD_COST] taskId={}, poolId={}, costMs={}, candidateSize={}",
                    taskId, pool.getId(), System.currentTimeMillis() - loadStart, candidates.size());

            long planStart = System.currentTimeMillis();
            BatchAssignPlan plan = buildBatchAssignPlan(candidates, assignUserIds, preparedData, recentOwnerMap);
            log.info("[POOL_BATCH_ASSIGN_PLAN_COST] taskId={}, poolId={}, costMs={}, assignedSize={}, unassignedSize={}",
                    taskId, pool.getId(), System.currentTimeMillis() - planStart,
                    plan.getAssignedCustomerIds().size(), plan.getUnassignedCustomerIds().size());

            if (CollectionUtils.isNotEmpty(plan.getAssignedCustomerIds())) {
                transactionTemplate.executeWithoutResult(status ->
                        executeBatchAssignPlan(taskId, pool, plan, currentOrgId, currentUser, defaultStage, defaultStageStatus));
                sendBatchAssignSummaryNotice(taskId, pool, plan, currentOrgId, currentUser);
            }

            log.info("[POOL_BATCH_ASSIGN_FINISH] taskId={}, poolId={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, pool.getId(), plan.getAssignedCustomerIds().size(), plan.getUnassignedCustomerIds().size(),
                    System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            log.error("[POOL_BATCH_ASSIGN_FAILED] taskId={}, poolId={}, operator={}",
                    taskId, pool.getId(), currentUser, ex);
        } finally {
            releasePoolBatchLock(lock, lock.getName(), lockThreadId);
        }
    }

    /**
     * 异步执行批量转移主流程：
     * 1. 一次性加载批量转移需要的基础数据
     * 2. 过滤当前仍在源公海池中的客户
     * 3. 按固定批次批量更新客户并批量写日志
     */
    private void doBatchTransferAsync(String taskId, long lockThreadId, CustomerPool sourcePool, CustomerPool targetPool,
                                      PoolBatchTransferRequest request, String currentUser, String currentOrgId, RLock lock) {
        long totalStart = System.currentTimeMillis();
        try {
            log.info("[POOL_BATCH_TRANSFER_START] taskId={}, sourcePoolId={}, targetPoolId={}, operator={}, batchSize={}",
                    taskId, sourcePool.getId(), targetPool.getId(), currentUser, request.getBatchIds().size());

            long loadStart = System.currentTimeMillis();
            Map<String, Integer> requestOrderMap = new HashMap<>();
            for (int index = 0; index < request.getBatchIds().size(); index++) {
                requestOrderMap.put(request.getBatchIds().get(index), index);
            }
            List<Customer> customers = customerMapper.selectByIds(request.getBatchIds());
            List<Customer> candidates = customers.stream()
                    .filter(Objects::nonNull)
                    .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                    .filter(customer -> Strings.CS.equals(sourcePool.getId(), customer.getPoolId()))
                    .sorted(Comparator.comparingInt(customer -> requestOrderMap.getOrDefault(customer.getId(), Integer.MAX_VALUE)))
                    .toList();
            log.info("[POOL_BATCH_TRANSFER_LOAD_COST] taskId={}, sourcePoolId={}, costMs={}, candidateSize={}",
                    taskId, sourcePool.getId(), System.currentTimeMillis() - loadStart, candidates.size());

            long planStart = System.currentTimeMillis();
            BatchTransferPlan plan = buildBatchTransferPlan(request.getBatchIds(), candidates);
            log.info("[POOL_BATCH_TRANSFER_PLAN_COST] taskId={}, sourcePoolId={}, costMs={}, transferSize={}, skippedSize={}",
                    taskId, sourcePool.getId(), System.currentTimeMillis() - planStart,
                    plan.transferCustomers().size(), plan.skippedCustomerIds().size());

            if (CollectionUtils.isNotEmpty(plan.transferCustomers())) {
                transactionTemplate.executeWithoutResult(status ->
                        executeBatchTransferPlan(taskId, sourcePool, targetPool, plan, currentUser, currentOrgId));
            }

            log.info("[POOL_BATCH_TRANSFER_FINISH] taskId={}, sourcePoolId={}, targetPoolId={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, sourcePool.getId(), targetPool.getId(), plan.transferCustomers().size(),
                    plan.skippedCustomerIds().size(), System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            log.error("[POOL_BATCH_TRANSFER_FAILED] taskId={}, sourcePoolId={}, targetPoolId={}, operator={}",
                    taskId, sourcePool.getId(), targetPool.getId(), currentUser, ex);
        } finally {
            releasePoolBatchLock(lock, lock.getName(), lockThreadId);
        }
    }

    /**
     * 异步执行批量编辑主流程：
     * 1. 重新过滤当前仍在目标公海池中的客户
     * 2. 走公海专用批量编辑逻辑，避免回退到老的全局唯一手机号规则
     */
    private void doBatchUpdateAsync(String taskId, long lockThreadId, CustomerPool pool, ResourceBatchEditRequest request,
                                    String currentUser, String currentOrgId, RLock lock) {
        long totalStart = System.currentTimeMillis();
        boolean mobileBatchUpdate = false;
        Customer targetCustomer = null;
        try {
            log.info("[POOL_BATCH_UPDATE_START] taskId={}, poolId={}, operator={}, batchSize={}, fieldId={}",
                    taskId, pool.getId(), currentUser, request.getIds().size(), request.getFieldId());

            BaseField field = customerFieldService.getAndCheckField(request.getFieldId(), currentOrgId);
            mobileBatchUpdate = Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey());

            long loadStart = System.currentTimeMillis();
            Map<String, Integer> requestOrderMap = new HashMap<>();
            for (int index = 0; index < request.getIds().size(); index++) {
                requestOrderMap.put(request.getIds().get(index), index);
            }
            List<Customer> customers = customerMapper.selectByIds(request.getIds());
            List<Customer> candidates = customers.stream()
                    .filter(Objects::nonNull)
                    .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                    .filter(customer -> Strings.CS.equals(pool.getId(), customer.getPoolId()))
                    .sorted(Comparator.comparingInt(customer -> requestOrderMap.getOrDefault(customer.getId(), Integer.MAX_VALUE)))
                    .toList();
            log.info("[POOL_BATCH_UPDATE_LOAD_COST] taskId={}, poolId={}, costMs={}, candidateSize={}",
                    taskId, pool.getId(), System.currentTimeMillis() - loadStart, candidates.size());

            if (CollectionUtils.isEmpty(candidates)) {
                log.info("[POOL_BATCH_UPDATE_SKIP] taskId={}, poolId={}, reason=no_candidate", taskId, pool.getId());
                return;
            }
            targetCustomer = candidates.getFirst();

            ResourceBatchEditRequest batchEditRequest = new ResourceBatchEditRequest();
            batchEditRequest.setIds(candidates.stream().map(Customer::getId).toList());
            batchEditRequest.setFieldId(request.getFieldId());
            batchEditRequest.setFieldValue(request.getFieldValue());
            transactionTemplate.executeWithoutResult(status ->
                    executePoolBatchUpdate(batchEditRequest, candidates, field, currentUser, currentOrgId));

            if (mobileBatchUpdate) {
                sendPoolMobileBatchUpdateNotice(taskId, pool, targetCustomer, currentOrgId, currentUser, true, null);
            }

            log.info("[POOL_BATCH_UPDATE_FINISH] taskId={}, poolId={}, successCount={}, totalCostMs={}",
                    taskId, pool.getId(), candidates.size(), System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            if (mobileBatchUpdate) {
                sendPoolMobileBatchUpdateNotice(taskId, pool, targetCustomer, currentOrgId, currentUser, false, ex.getMessage());
            }
            log.error("[POOL_BATCH_UPDATE_FAILED] taskId={}, poolId={}, operator={}, fieldId={}",
                    taskId, pool.getId(), currentUser, request.getFieldId(), ex);
        } finally {
            releasePoolBatchLock(lock, lock.getName(), lockThreadId);
        }
    }

    private void executePoolBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers,
                                        String currentUser, String currentOrgId) {
        BaseField field = customerFieldService.getAndCheckField(request.getFieldId(), currentOrgId);
        executePoolBatchUpdate(request, originCustomers, field, currentUser, currentOrgId);
    }

    private void executePoolBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers, BaseField field,
                                        String currentUser, String currentOrgId) {
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey())) {
            executePoolMobileBatchUpdate(request, originCustomers, field, currentUser, currentOrgId);
            return;
        }
        customerFieldService.batchUpdate(request, field, originCustomers, Customer.class, LogModule.CUSTOMER_POOL,
                this::executeCustomerBatchUpdate, currentUser, currentOrgId);
    }

    private void executePoolMobileBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers, BaseField field,
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

    private void executeCustomerBatchUpdate(BatchUpdateDbParam updateParam) {
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

    private void sendPoolMobileBatchUpdateNotice(String taskId, CustomerPool pool, Customer customer, String currentOrgId,
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

    private BatchPickPreparedData prepareBatchPickData(List<Customer> candidates, String currentUser, String currentOrgId,
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

    private BatchPickPlan buildBatchPickPlan(List<Customer> candidates, String currentUser, CustomerPoolPickRule pickRule,
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

    private void executeBatchPickPlan(String taskId, CustomerPool pool, BatchPickPlan plan, Map<String, String> recentOwnerMap,
                                      String currentUser, String currentOrgId, String defaultStage, String defaultStageStatus) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        List<String> pickedCustomerIds = plan.pickedCustomers().stream().map(Customer::getId).toList();
        for (List<String> partitionIds : partition(pickedCustomerIds, BATCH_ASSIGN_UPDATE_SIZE)) {
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
            for (List<String> partitionIds : partition(oldOwnerEntry.getValue(), BATCH_ASSIGN_UPDATE_SIZE)) {
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

    private CustomerPoolPickRule loadPoolPickRule(String poolId) {
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

    private BatchTransferPlan buildBatchTransferPlan(List<String> requestIds, List<Customer> candidates) {
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

    private void executeBatchTransferPlan(String taskId, CustomerPool sourcePool, CustomerPool targetPool, BatchTransferPlan plan,
                                          String currentUser, String currentOrgId) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        List<String> transferIds = plan.transferCustomers().stream().map(Customer::getId).toList();
        for (List<String> partitionIds : partition(transferIds, BATCH_ASSIGN_UPDATE_SIZE)) {
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
    private BatchAssignPreparedData prepareBatchAssignData(List<String> assignUserIds, List<Customer> candidates, String currentOrgId) {
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
    private BatchAssignPlan buildBatchAssignPlan(List<Customer> candidates, List<String> assignUserIds,
                                                 BatchAssignPreparedData preparedData, Map<String, String> recentOwnerMap) {
        Map<String, List<Customer>> ownerCustomersMap = new LinkedHashMap<>();
        List<String> assignedCustomerIds = new ArrayList<>();
        List<String> unassignedCustomerIds = new ArrayList<>();
        if (CollectionUtils.isEmpty(candidates) || CollectionUtils.isEmpty(assignUserIds)) {
            candidates.stream().map(Customer::getId).filter(Objects::nonNull).forEach(unassignedCustomerIds::add);
            return new BatchAssignPlan(ownerCustomersMap, assignedCustomerIds, unassignedCustomerIds, recentOwnerMap);
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
        return new BatchAssignPlan(ownerCustomersMap, assignedCustomerIds, unassignedCustomerIds, recentOwnerMap);
    }

    /**
     * 按分配方案批量落库。
     * 这里不再复用旧的 ownCustomer()，而是把客户更新、负责人历史删除、联系人负责人更新、
     * 微信好友状态重算、日志写入拆成批量动作。
     */
    private void executeBatchAssignPlan(String taskId, CustomerPool pool, BatchAssignPlan plan, String currentOrgId,
                                        String currentUser, String defaultStage, String defaultStageStatus) {
        long updateCustomerStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<Customer>> entry : plan.getOwnerCustomersMap().entrySet()) {
            String ownerId = entry.getKey();
            List<String> customerIds = entry.getValue().stream().map(Customer::getId).toList();
            for (List<String> partitionIds : partition(customerIds, BATCH_ASSIGN_UPDATE_SIZE)) {
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
                for (List<String> partitionIds : partition(oldOwnerEntry.getValue(), BATCH_ASSIGN_UPDATE_SIZE)) {
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
    private void sendBatchAssignSummaryNotice(String taskId, CustomerPool pool, BatchAssignPlan plan, String currentOrgId, String currentUser) {
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
    private Map<String, String> buildRecentOwnerMap(List<Customer> customers) {
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
    private List<String> resolveAssignUserIds(PoolBatchAssignRequest request, String assignUserId) {
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
    private String resolvePoolIdForBatchAssign(List<Customer> customers) {
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
    private String resolveSourcePoolIdForBatchTransfer(List<Customer> customers) {
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

    /**
     * 统一拼租户隔离后的 Redis key，避免不同租户之间锁冲突。
     */
    private String tenantRedisKey(String rawKey) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.isNotBlank(tenantId) ? tenantId + ":" + rawKey : rawKey;
    }

    /**
     * 统一释放当前批量分配持有的 Redisson 锁。
     */
    private void releasePoolBatchLock(RLock lock, String lockKey, long lockThreadId) {
        try {
            if (lock != null && lock.isHeldByThread(lockThreadId)) {
                lock.unlockAsync(lockThreadId).get();
            }
        } catch (Exception ex) {
            log.warn("[POOL_BATCH_UNLOCK_FAILED] lockKey={}", lockKey, ex);
        }
    }

    /**
     * 按固定大小切分 ID，避免单条 SQL 的 IN 过长。
     */
    private List<List<String>> partition(List<String> ids, int batchSize) {
        List<List<String>> partitions = new ArrayList<>();
        if (CollectionUtils.isEmpty(ids)) {
            return partitions;
        }
        for (int index = 0; index < ids.size(); index += batchSize) {
            partitions.add(ids.subList(index, Math.min(index + batchSize, ids.size())));
        }
        return partitions;
    }

    private record BatchPickPreparedData(Map<String, CustomerOwner> lastOwnerMap,
                                         Set<String> privateConflictMobiles,
                                         Set<String> ownedPoolMobiles,
                                         int remainingCapacity,
                                         Integer remainingDailyPick) {
    }

    private record BatchPickPlan(List<Customer> pickedCustomers, List<String> skippedCustomerIds) {
    }

    private record BatchAssignPreparedData(Map<String, Integer> userCapacitiesMap,
                                           Map<String, Set<String>> userOwnedPoolMobileMap,
                                           Map<String, Set<String>> userPrivateConflictMobileMap) {
    }

    private record BatchTransferPlan(List<Customer> transferCustomers, List<String> skippedCustomerIds) {
    }

    private static final class BatchAssignPlan {

        private final Map<String, List<Customer>> ownerCustomersMap;
        private final List<String> assignedCustomerIds;
        private final List<String> unassignedCustomerIds;
        private final Map<String, String> recentOwnerMap;

        private BatchAssignPlan(Map<String, List<Customer>> ownerCustomersMap, List<String> assignedCustomerIds,
                                List<String> unassignedCustomerIds, Map<String, String> recentOwnerMap) {
            this.ownerCustomersMap = ownerCustomersMap;
            this.assignedCustomerIds = assignedCustomerIds;
            this.unassignedCustomerIds = unassignedCustomerIds;
            this.recentOwnerMap = recentOwnerMap;
        }

        public Map<String, List<Customer>> getOwnerCustomersMap() {
            return ownerCustomersMap;
        }

        public List<String> getAssignedCustomerIds() {
            return assignedCustomerIds;
        }

        public List<String> getUnassignedCustomerIds() {
            return unassignedCustomerIds;
        }

        public Map<String, String> getRecentOwnerMap() {
            return recentOwnerMap;
        }
    }
}
