package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.transaction.TenantTransactionExecutor;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.customer.constants.PoolCustomerBatchConstants;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.customer.domain.CustomerPoolPickRule;
import cn.cordys.crm.customer.dto.request.*;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
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
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.crm.system.service.UserExtendService;
import cn.cordys.mybatis.BaseMapper;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import static cn.cordys.crm.customer.service.PoolCustomerBatchTypes.*;

@Slf4j
@Service
public class PoolCustomerBatchByConditionService {

    private static final int BATCH_DELETE_BY_CONDITION_PAGE_SIZE = 2000;
    private static final int BATCH_PICK_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_ASSIGN_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_TRANSFER_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_UPDATE_BY_CONDITION_MAX_SIZE = 2000;
    private static final int CHUNK_SIZE = 200;
    private static final String BATCH_POOL_OPERATION_LOCK_PREFIX = "crm:pool:batch-op:";

    private enum Op {
        DELETE("DELETE", "批量删除"),
        PICK("PICK", "批量领取"),
        ASSIGN("ASSIGN", "批量分配"),
        TRANSFER("TRANSFER", "批量转移"),
        UPDATE("UPDATE", "批量编辑");

        final String code;
        final String label;

        Op(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    @FunctionalInterface
    private interface AsyncTask {
        void run(String taskId, String tenantId);
    }

    @Resource
    private PoolCustomerBatchSupport batchSupport;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<CustomerPool> poolMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private UserExtendService userExtendService;
    @Resource
    private InSiteNoticeSender inSiteNoticeSender;
    @Resource
    private SseService sseService;
    @Resource(name = ExecutorBeanNames.BATCH)
    private Executor batchExecutor;
    @Resource
    private TenantTransactionExecutor tenantTransactionExecutor;
    @Resource
    private Redisson redisson;

    public Map<String, Object> batchDeleteByCondition(CustomerPageRequest request, String userId, String orgId) {
        if (StringUtils.isBlank(request.getPoolId())) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerPageRequest asyncRequest = BeanUtils.copyBean(new CustomerPageRequest(), request);
        return submit(request.getPoolId(), userId, Op.DELETE, "批量删除任务已提交",
                (taskId, tenantId) ->
                        doBatchDeleteByConditionAsync(taskId, tenantId, asyncRequest, userId, orgId));
    }

    /**
     * 按筛选条件批量领取客户（取当前筛选排序下的前 pickCount 条）。
     */
    public Map<String, Object> batchPickByCondition(PoolBatchPickByConditionRequest request, String currentUser, String currentOrgId) {
        if (StringUtils.isBlank(request.getPoolId()) || request.getPickCount() == null || request.getPickCount() < 1) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerPool pool = poolMapper.selectByPrimaryKey(request.getPoolId());
        if (pool == null) {
            throw new GenericException(Translator.get("customer_pool_not_exist"));
        }
        PoolBatchPickByConditionRequest asyncRequest = BeanUtils.copyBean(new PoolBatchPickByConditionRequest(), request);
        return submit(request.getPoolId(), currentUser, Op.PICK, "批量领取任务已提交",
                (taskId, tenantId) ->
                        doBatchPickByConditionAsync(taskId, tenantId, asyncRequest, pool, currentUser, currentOrgId));
    }

    /**
     * 按筛选条件批量分配客户（取当前筛选排序下的前 assignCount 条）。
     */
    public Map<String, Object> batchAssignByCondition(PoolBatchAssignByConditionRequest request, String currentOrgId, String currentUser) {
        PoolBatchAssignRequest probe = new PoolBatchAssignRequest();
        probe.setAssignUserId(request.getAssignUserId());
        probe.setAssignUserIds(request.getAssignUserIds());
        List<String> assignUserIds = batchSupport.resolveAssignUserIds(probe, request.getAssignUserId());
        if (CollectionUtils.isEmpty(assignUserIds)) {
            throw new GenericException(Translator.get("user.not.exist"));
        }
        if (StringUtils.isBlank(request.getPoolId()) || request.getAssignCount() == null || request.getAssignCount() < 1) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        PoolBatchAssignByConditionRequest asyncRequest = BeanUtils.copyBean(new PoolBatchAssignByConditionRequest(), request);
        return submit(request.getPoolId(), currentUser, Op.ASSIGN, "批量分配任务已提交",
                (taskId, tenantId) ->
                        doBatchAssignByConditionAsync(taskId, tenantId, asyncRequest, assignUserIds, currentOrgId, currentUser));
    }

    /**
     * 按筛选条件批量转移客户（取当前筛选排序下的前 transferCount 条）。
     */
    public Map<String, Object> batchTransferByCondition(PoolBatchTransferByConditionRequest request, String currentUser, String currentOrgId) {
        if (StringUtils.isBlank(request.getPoolId()) || StringUtils.isBlank(request.getTargetPoolId())
                || request.getTransferCount() == null || request.getTransferCount() < 1) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerPool targetPool = poolMapper.selectByPrimaryKey(request.getTargetPoolId());
        if (targetPool == null) {
            throw new GenericException(Translator.get("pool_import_pool_not_exist"));
        }
        if (!Boolean.TRUE.equals(targetPool.getEnable())) {
            throw new GenericException(Translator.get("pool_import_pool_disabled"));
        }
        PoolBatchTransferByConditionRequest asyncRequest = BeanUtils.copyBean(new PoolBatchTransferByConditionRequest(), request);
        return submit(request.getPoolId(), currentUser, Op.TRANSFER, "批量转移任务已提交",
                (taskId, tenantId) ->
                        doBatchTransferByConditionAsync(taskId, tenantId, asyncRequest, targetPool, currentUser, currentOrgId));
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
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey()) && updateCount > 1) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }
        if (field.needRepeatCheck() && updateCount > 1 && request.getFieldValue() != null
                && StringUtils.isNotBlank(String.valueOf(request.getFieldValue()))) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }
        PoolBatchUpdateByConditionRequest asyncRequest = BeanUtils.copyBean(new PoolBatchUpdateByConditionRequest(), request);
        return submit(request.getPoolId(), currentUser, Op.UPDATE, "批量编辑任务已提交",
                (taskId, tenantId) ->
                        doBatchUpdateByConditionAsync(taskId, tenantId, asyncRequest, pool, field, currentUser, currentOrgId));
    }

    /**
     * 异步执行按筛选条件批量删除。
     */
    private void doBatchDeleteByConditionAsync(String taskId, String tenantId, CustomerPageRequest request,
                                               String userId, String orgId) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.DELETE;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        CustomerPool pool = poolMapper.selectByPrimaryKey(request.getPoolId());
        String poolName = pool == null ? request.getPoolId() : pool.getName();
        try {
            log.info("[POOL_BATCH_DELETE_START] taskId={}, poolId={}, operator={}", taskId, request.getPoolId(), userId);

            long collectStart = System.currentTimeMillis();
            List<String> matchedIds = collectIdsUnlimited(request, userId, orgId);
            submittedCount = matchedIds.size();
            log.info("[POOL_BATCH_DELETE_COLLECT_COST] taskId={}, poolId={}, costMs={}, submittedCount={}",
                    taskId, request.getPoolId(), System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(matchedIds)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getPoolId(), poolName, 0, 0, 0, "未查询到可删除客户");
                return;
            }

            BatchChunkResult chunkResult = runParallelIdChunks(tenantId, taskId, op.code, batchSupport.partition(matchedIds, CHUNK_SIZE),
                    chunkIds -> {
                        batchSupport.batchDelete(chunkIds, userId, orgId);
                        return chunkIds.size();
                    });
            successCount = chunkResult.successCount();
            failCount = chunkResult.failCount();

            log.info("[POOL_BATCH_DELETE_FINISH] taskId={}, poolId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, request.getPoolId(), submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getPoolId(), poolName, submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            failCount = Math.max(failCount, submittedCount - successCount);
            log.error("[POOL_BATCH_DELETE_FAILED] taskId={}, poolId={}, operator={}, submittedCount={}, successCount={}, failCount={}",
                    taskId, request.getPoolId(), userId, submittedCount, successCount, failCount, ex);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getPoolId(), poolName, submittedCount, successCount, failCount, ex.getMessage());
        }
    }

    private void doBatchPickByConditionAsync(String taskId, String tenantId,
                                             PoolBatchPickByConditionRequest request, CustomerPool pool,
                                             String currentUser, String currentOrgId) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.PICK;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        try {
            log.info("[POOL_BATCH_PICK_START] taskId={}, poolId={}, operator={}", taskId, pool.getId(), currentUser);

            int pickCount = Math.min(request.getPickCount(), BATCH_PICK_BY_CONDITION_MAX_SIZE);
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, currentOrgId, currentUser, pickCount);
            submittedCount = ids.size();
            log.info("[POOL_BATCH_PICK_COLLECT_COST] taskId={}, poolId={}, costMs={}, submittedCount={}",
                    taskId, pool.getId(), System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), 0, 0, 0, "未查询到可领取客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> customers = customerMapper.selectByIds(ids);
            List<Customer> candidates = filterPoolCandidates(customers, pool.getId(), requestOrderMap);
            Map<String, String> recentOwnerMap = batchSupport.buildRecentOwnerMap(candidates);
            List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(currentOrgId);
            String defaultStage = CollectionUtils.isNotEmpty(stageConfigList) ? stageConfigList.getFirst().getId() : null;
            String defaultStageStatus = CollectionUtils.isNotEmpty(stageConfigList) ? CustomerStageService.STATUS_NEW : null;
            CustomerPoolPickRule pickRule = batchSupport.loadPoolPickRule(pool.getId());
            boolean poolAdmin = userExtendService.isPoolAdmin(JSON.parseArray(pool.getOwnerId(), String.class), currentUser, currentOrgId);
            BatchPickPreparedData preparedData = batchSupport.prepareBatchPickData(candidates, currentUser, currentOrgId, pickRule, poolAdmin);
            BatchPickPlan plan = batchSupport.buildBatchPickPlan(candidates, currentUser, pickRule, poolAdmin, preparedData);
            failCount = plan.skippedCustomerIds().size() + (submittedCount - candidates.size());

            if (CollectionUtils.isNotEmpty(plan.pickedCustomers())) {
                List<List<Customer>> chunks = partitionCustomers(plan.pickedCustomers(), CHUNK_SIZE);
                AtomicInteger pickedFail = new AtomicInteger(0);
                runParallelCustomerChunks(tenantId, taskId, op.code, chunks, chunk ->
                        batchSupport.executeBatchPickPlanChunk(taskId, pool, chunk, recentOwnerMap, currentUser, currentOrgId,
                                defaultStage, defaultStageStatus), pickedFail);
                successCount = plan.pickedCustomers().size() - pickedFail.get();
                failCount += pickedFail.get();
            }

            log.info("[POOL_BATCH_PICK_FINISH] taskId={}, poolId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, pool.getId(), submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            log.error("[POOL_BATCH_PICK_FAILED] taskId={}, poolId={}, operator={}", taskId, pool.getId(), currentUser, ex);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private void doBatchAssignByConditionAsync(String taskId, String tenantId,
                                               PoolBatchAssignByConditionRequest request, List<String> assignUserIds,
                                               String currentOrgId, String currentUser) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.ASSIGN;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        CustomerPool pool = null;
        try {
            int assignCount = Math.min(request.getAssignCount(), BATCH_ASSIGN_BY_CONDITION_MAX_SIZE);
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, currentOrgId, currentUser, assignCount);
            submittedCount = ids.size();
            log.info("[POOL_BATCH_ASSIGN_START] taskId={}, poolId={}, operator={}, submittedCount={}, assignUserCount={}",
                    taskId, request.getPoolId(), currentUser, submittedCount, assignUserIds.size());
            log.info("[POOL_BATCH_ASSIGN_COLLECT_COST] taskId={}, poolId={}, costMs={}, submittedCount={}",
                    taskId, request.getPoolId(), System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                pool = poolMapper.selectByPrimaryKey(request.getPoolId());
                String poolName = pool == null ? request.getPoolId() : pool.getName();
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, request.getPoolId(), poolName, 0, 0, 0, "未查询到可分配客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> customers = customerMapper.selectByIds(ids);
            pool = poolMapper.selectByPrimaryKey(batchSupport.resolvePoolIdForBatchAssign(customers));
            List<Customer> candidates = filterPoolCandidates(customers, pool.getId(), requestOrderMap);
            List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(currentOrgId);
            String defaultStage = CollectionUtils.isNotEmpty(stageConfigList) ? stageConfigList.getFirst().getId() : null;
            String defaultStageStatus = CollectionUtils.isNotEmpty(stageConfigList) ? CustomerStageService.STATUS_NEW : null;
            Map<String, String> recentOwnerMap = batchSupport.buildRecentOwnerMap(candidates);
            BatchAssignPreparedData preparedData = batchSupport.prepareBatchAssignData(assignUserIds, candidates, currentOrgId);
            PoolCustomerBatchTypes.BatchAssignPlan plan = batchSupport.buildBatchAssignPlan(candidates, assignUserIds, preparedData, recentOwnerMap);
            failCount = plan.getUnassignedCustomerIds().size() + (submittedCount - candidates.size());

            if (CollectionUtils.isNotEmpty(plan.getAssignedCustomerIds())) {
                final CustomerPool assignPool = pool;
                final String assignDefaultStage = defaultStage;
                final String assignDefaultStageStatus = defaultStageStatus;
                List<AssignChunkTask> chunkTasks = buildAssignChunkTasks(plan, CHUNK_SIZE);
                AtomicInteger assignedSuccess = new AtomicInteger(0);
                AtomicInteger assignedFail = new AtomicInteger(0);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (AssignChunkTask chunkTask : chunkTasks) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            tenantTransactionExecutor.executeInNewTransaction(tenantId, () ->
                                    batchSupport.executeBatchAssignPlanChunk(taskId, assignPool, chunkTask.ownerId(), chunkTask.customers(),
                                            chunkTask.recentOwnerMap(), currentOrgId, currentUser, assignDefaultStage, assignDefaultStageStatus));
                            assignedSuccess.addAndGet(chunkTask.customers().size());
                        } catch (Exception ex) {
                            assignedFail.addAndGet(chunkTask.customers().size());
                            log.warn("[POOL_BATCH_ASSIGN_CHUNK_FAILED] taskId={}, poolId={}, ownerId={}, chunkSize={}",
                                    taskId, assignPool.getId(), chunkTask.ownerId(), chunkTask.customers().size(), ex);
                        }
                    }, batchExecutor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                successCount = assignedSuccess.get();
                failCount += assignedFail.get();
                batchSupport.sendBatchAssignSummaryNotice(taskId, pool, plan, currentOrgId, currentUser);
            }

            log.info("[POOL_BATCH_ASSIGN_FINISH] taskId={}, poolId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, pool.getId(), submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            String poolName = pool == null ? request.getPoolId() : pool.getName();
            String poolId = pool == null ? request.getPoolId() : pool.getId();
            log.error("[POOL_BATCH_ASSIGN_FAILED] taskId={}, poolId={}, operator={}", taskId, request.getPoolId(), currentUser, ex);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, poolId, poolName, submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private void doBatchTransferByConditionAsync(String taskId, String tenantId,
                                               PoolBatchTransferByConditionRequest request, CustomerPool targetPool,
                                               String currentUser, String currentOrgId) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.TRANSFER;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        CustomerPool sourcePool = null;
        try {
            int transferCount = Math.min(request.getTransferCount(), BATCH_TRANSFER_BY_CONDITION_MAX_SIZE);
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, currentOrgId, currentUser, transferCount);
            submittedCount = ids.size();
            log.info("[POOL_BATCH_TRANSFER_START] taskId={}, sourcePoolId={}, targetPoolId={}, operator={}, submittedCount={}",
                    taskId, request.getPoolId(), targetPool.getId(), currentUser, submittedCount);
            log.info("[POOL_BATCH_TRANSFER_COLLECT_COST] taskId={}, poolId={}, costMs={}, submittedCount={}",
                    taskId, request.getPoolId(), System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, request.getPoolId(), request.getPoolId(), 0, 0, 0, "未查询到可转移客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> customers = customerMapper.selectByIds(ids);
            sourcePool = poolMapper.selectByPrimaryKey(batchSupport.resolveSourcePoolIdForBatchTransfer(customers));
            List<Customer> candidates = filterPoolCandidates(customers, sourcePool.getId(), requestOrderMap);
            BatchTransferPlan plan = batchSupport.buildBatchTransferPlan(ids, candidates);
            failCount = plan.skippedCustomerIds().size() + (submittedCount - candidates.size());

            if (CollectionUtils.isNotEmpty(plan.transferCustomers())) {
                final CustomerPool transferSourcePool = sourcePool;
                List<List<Customer>> chunks = partitionCustomers(plan.transferCustomers(), CHUNK_SIZE);
                AtomicInteger transferFail = new AtomicInteger(0);
                runParallelCustomerChunks(tenantId, taskId, op.code, chunks, chunk ->
                        batchSupport.executeBatchTransferPlanChunk(taskId, transferSourcePool, targetPool, chunk, currentUser, currentOrgId), transferFail);
                successCount = plan.transferCustomers().size() - transferFail.get();
                failCount += transferFail.get();
            }

            log.info("[POOL_BATCH_TRANSFER_FINISH] taskId={}, sourcePoolId={}, targetPoolId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, sourcePool.getId(), targetPool.getId(), submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, sourcePool.getId(), sourcePool.getName(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            String poolName = sourcePool == null ? request.getPoolId() : sourcePool.getName();
            String poolId = sourcePool == null ? request.getPoolId() : sourcePool.getId();
            log.error("[POOL_BATCH_TRANSFER_FAILED] taskId={}, sourcePoolId={}, targetPoolId={}, operator={}",
                    taskId, request.getPoolId(), targetPool.getId(), currentUser, ex);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, poolId, poolName, submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private void doBatchUpdateByConditionAsync(String taskId, String tenantId,
                                               PoolBatchUpdateByConditionRequest request, CustomerPool pool, BaseField field,
                                               String currentUser, String currentOrgId) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.UPDATE;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        boolean mobileBatchUpdate = Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey());
        Customer targetCustomer = null;
        try {
            int updateCount = Math.min(request.getUpdateCount(), BATCH_UPDATE_BY_CONDITION_MAX_SIZE);
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, currentOrgId, currentUser, updateCount);
            submittedCount = ids.size();
            log.info("[POOL_BATCH_UPDATE_START] taskId={}, poolId={}, operator={}, submittedCount={}, fieldId={}",
                    taskId, pool.getId(), currentUser, submittedCount, request.getFieldId());
            log.info("[POOL_BATCH_UPDATE_COLLECT_COST] taskId={}, poolId={}, costMs={}, submittedCount={}",
                    taskId, pool.getId(), System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), 0, 0, 0, "未查询到可编辑客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> customers = customerMapper.selectByIds(ids);
            List<Customer> candidates = filterPoolCandidates(customers, pool.getId(), requestOrderMap);
            failCount = submittedCount - candidates.size();

            if (CollectionUtils.isEmpty(candidates)) {
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, 0, failCount, null);
                return;
            }

            ResourceBatchEditRequest batchEditRequest = new ResourceBatchEditRequest();
            batchEditRequest.setFieldId(request.getFieldId());
            batchEditRequest.setFieldValue(request.getFieldValue());

            if (mobileBatchUpdate) {
                if (candidates.size() > 1) {
                    throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
                }
                targetCustomer = candidates.getFirst();
                batchEditRequest.setIds(List.of(targetCustomer.getId()));
                tenantTransactionExecutor.executeInNewTransaction(tenantId, () ->
                        batchSupport.executePoolBatchUpdate(batchEditRequest, candidates, field, currentUser, currentOrgId));
                successCount = 1;
                batchSupport.sendPoolMobileBatchUpdateNotice(taskId, pool, targetCustomer, currentOrgId, currentUser, true, null);
            } else {
                List<List<Customer>> chunks = partitionCustomers(candidates, CHUNK_SIZE);
                AtomicInteger updateFail = new AtomicInteger(0);
                runParallelCustomerChunks(tenantId, taskId, op.code, chunks, chunk -> {
                    ResourceBatchEditRequest chunkRequest = new ResourceBatchEditRequest();
                    chunkRequest.setIds(chunk.stream().map(Customer::getId).toList());
                    chunkRequest.setFieldId(request.getFieldId());
                    chunkRequest.setFieldValue(request.getFieldValue());
                    batchSupport.executePoolBatchUpdate(chunkRequest, chunk, field, currentUser, currentOrgId);
                }, updateFail);
                successCount = candidates.size() - updateFail.get();
                failCount += updateFail.get();
                sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, successCount, failCount, null);
            }

            log.info("[POOL_BATCH_UPDATE_FINISH] taskId={}, poolId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, pool.getId(), submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
        } catch (Exception ex) {
            if (mobileBatchUpdate) {
                batchSupport.sendPoolMobileBatchUpdateNotice(taskId, pool, targetCustomer, currentOrgId, currentUser, false, ex.getMessage());
            }
            log.error("[POOL_BATCH_UPDATE_FAILED] taskId={}, poolId={}, operator={}, fieldId={}",
                    taskId, pool.getId(), currentUser, request.getFieldId(), ex);
            sendOperatorNotice(taskId, tenantId, currentOrgId, currentUser, op, pool.getId(), pool.getName(), submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private Map<String, Object> submit(String poolId, String operator, Op op,
                                                         String acceptedMessage, AsyncTask task) {
        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_POOL_OPERATION_LOCK_PREFIX + poolId);
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            // 请求线程加锁；leaseTime=-1 由 Redisson watchdog 续期；threadId 供 BATCH 线程 unlockAsync
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException(op.label + "加锁失败");
        }
        if (!locked) {
            log.info("[POOL_BATCH_{}_LOCK_REJECTED] poolId={}, operator={}, lockKey={}", op.code, poolId, operator, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "该公海池已有批量任务执行中，请稍后再试"
            );
        }
        final String tenantId;
        try {
            tenantId = TenantContext.requireTenantId();
            batchExecutor.execute(() -> {
                try {
                    task.run(taskId, tenantId);
                } finally {
                    releaseLock(lock, lockKey, lockThreadId);
                }
            });
        } catch (Exception ex) {
            releaseLock(lock, lockKey, lockThreadId);
            throw ex;
        }
        log.info("[POOL_BATCH_{}_SUBMIT] taskId={}, poolId={}, operator={}", op.code, taskId, poolId, operator);
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", acceptedMessage
        );
    }

    private List<String> collectIdsLimited(CustomerPageRequest request, String orgId, String userId, int maxSize) {
        if (maxSize <= 0) {
            return List.of();
        }
        PageHelper.startPage(1, maxSize, false);
        List<String> ids = extCustomerMapper.listIds(request, orgId, userId, null);
        return CollectionUtils.isEmpty(ids) ? List.of() : new ArrayList<>(ids);
    }

    private List<String> collectIdsUnlimited(CustomerPageRequest request, String userId, String orgId) {
        List<String> matchedIds = new ArrayList<>();
        int pageNum = 1;
        PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_PAGE_SIZE, false);
        List<String> pageIds = extCustomerMapper.listIds(request, orgId, userId, null);
        while (CollectionUtils.isNotEmpty(pageIds)) {
            matchedIds.addAll(pageIds);
            if (pageIds.size() < BATCH_DELETE_BY_CONDITION_PAGE_SIZE) {
                return matchedIds;
            }
            pageNum++;
            PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_PAGE_SIZE, false);
            pageIds = extCustomerMapper.listIds(request, orgId, userId, null);
        }
        return matchedIds;
    }

    private BatchChunkResult runParallelIdChunks(String tenantId, String taskId, String operation,
                                                 List<List<String>> chunks, ToIntFunction<List<String>> chunkWorker) {
        if (CollectionUtils.isEmpty(chunks)) {
            return new BatchChunkResult(0, 0);
        }
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<String> chunk : chunks) {
            if (CollectionUtils.isEmpty(chunk)) {
                continue;
            }
            List<String> chunkCopy = new ArrayList<>(chunk);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    int processed = tenantTransactionExecutor.executeInNewTransaction(tenantId,
                            () -> chunkWorker.applyAsInt(chunkCopy));
                    successCount.addAndGet(processed);
                    if (processed < chunkCopy.size()) {
                        failCount.addAndGet(chunkCopy.size() - processed);
                    }
                    log.debug("[POOL_BATCH_CHUNK_DONE] taskId={}, operation={}, chunkSize={}, processed={}",
                            taskId, operation, chunkCopy.size(), processed);
                } catch (Exception ex) {
                    failCount.addAndGet(chunkCopy.size());
                    log.warn("[POOL_BATCH_CHUNK_FAILED] taskId={}, operation={}, chunkSize={}",
                            taskId, operation, chunkCopy.size(), ex);
                }
            }, batchExecutor));
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        return new BatchChunkResult(successCount.get(), failCount.get());
    }

    private void runParallelCustomerChunks(String tenantId, String taskId, String operation, List<List<Customer>> chunks,
                                           Consumer<List<Customer>> chunkWorker, AtomicInteger failCounter) {
        if (CollectionUtils.isEmpty(chunks)) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Customer> chunk : chunks) {
            if (CollectionUtils.isEmpty(chunk)) {
                continue;
            }
            List<Customer> chunkCopy = new ArrayList<>(chunk);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    tenantTransactionExecutor.executeInNewTransaction(tenantId, () -> chunkWorker.accept(chunkCopy));
                    log.debug("[POOL_BATCH_CHUNK_DONE] taskId={}, operation={}, chunkSize={}", taskId, operation, chunkCopy.size());
                } catch (Exception ex) {
                    failCounter.addAndGet(chunkCopy.size());
                    log.warn("[POOL_BATCH_CHUNK_FAILED] taskId={}, operation={}, chunkSize={}",
                            taskId, operation, chunkCopy.size(), ex);
                }
            }, batchExecutor));
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private Map<String, Integer> buildRequestOrderMap(List<String> ids) {
        Map<String, Integer> requestOrderMap = new HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            requestOrderMap.put(ids.get(index), index);
        }
        return requestOrderMap;
    }

    private List<Customer> filterPoolCandidates(List<Customer> customers, String poolId, Map<String, Integer> requestOrderMap) {
        return customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> Boolean.TRUE.equals(customer.getInSharedPool()))
                .filter(customer -> Strings.CS.equals(poolId, customer.getPoolId()))
                .sorted(Comparator.comparingInt(customer -> requestOrderMap.getOrDefault(customer.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    private List<List<Customer>> partitionCustomers(List<Customer> customers, int batchSize) {
        if (CollectionUtils.isEmpty(customers)) {
            return List.of();
        }
        List<String> ids = customers.stream().map(Customer::getId).toList();
        List<List<String>> idPartitions = batchSupport.partition(ids, batchSize);
        Map<String, Customer> customerMap = customers.stream()
                .collect(Collectors.toMap(Customer::getId, customer -> customer, (a, b) -> a, LinkedHashMap::new));
        List<List<Customer>> result = new ArrayList<>();
        for (List<String> idPartition : idPartitions) {
            List<Customer> chunk = new ArrayList<>();
            for (String id : idPartition) {
                Customer customer = customerMap.get(id);
                if (customer != null) {
                    chunk.add(customer);
                }
            }
            if (CollectionUtils.isNotEmpty(chunk)) {
                result.add(chunk);
            }
        }
        return result;
    }

    private List<AssignChunkTask> buildAssignChunkTasks(PoolCustomerBatchTypes.BatchAssignPlan plan, int chunkSize) {
        List<AssignChunkTask> tasks = new ArrayList<>();
        for (Map.Entry<String, List<Customer>> entry : plan.getOwnerCustomersMap().entrySet()) {
            String ownerId = entry.getKey();
            List<Customer> customers = entry.getValue();
            for (List<Customer> chunk : partitionCustomers(customers, chunkSize)) {
                Map<String, String> recentOwnerMap = new HashMap<>();
                for (Customer customer : chunk) {
                    recentOwnerMap.put(customer.getId(), plan.getRecentOwnerMap().get(customer.getId()));
                }
                tasks.add(new AssignChunkTask(ownerId, chunk, recentOwnerMap));
            }
        }
        return tasks;
    }

    private void sendOperatorNotice(String taskId, String tenantId, String orgId, String operatorUserId,
                                                    Op op, String poolId, String poolName,
                                                    int submitted, int success, int fail, String extraMessage) {
        String safePoolName = StringUtils.defaultIfBlank(poolName, "公海");
        String subjectText = "公海" + op.label + "通知";
        String resourceName = safePoolName + op.label + "任务完成";
        StringBuilder context = new StringBuilder();
        context.append("【").append(op.label).append("】任务完成：提交 ").append(submitted)
                .append("，成功 ").append(success).append("，失败 ").append(fail);
        if (StringUtils.isNotBlank(extraMessage)) {
            context.append("。").append(extraMessage);
        } else {
            context.append("。");
        }
        inSiteNoticeSender.sendAnnouncement(
                buildOperatorMessageDetail(taskId, orgId),
                buildOperatorNoticeModel(operatorUserId, orgId, operatorUserId, resourceName),
                context.toString(),
                subjectText
        );
        sendPoolBatchByConditionDoneSse(tenantId, operatorUserId, op, poolId, submitted, success, fail);
        log.info("[POOL_BATCH_{}_OPERATOR_NOTICE] taskId={}, poolName={}, submitted={}, success={}, fail={}",
                op.code, taskId, safePoolName, submitted, success, fail);
    }

    private void sendPoolBatchByConditionDoneSse(String tenantId, String operatorUserId, Op op, String poolId,
                                                 int submitted, int success, int fail) {
        if (op != Op.PICK && op != Op.ASSIGN && op != Op.TRANSFER) {
            return;
        }
        if (StringUtils.isAnyBlank(tenantId, poolId, operatorUserId)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", PoolCustomerBatchConstants.SSE_POOL_BATCH_BY_CONDITION_DONE);
        payload.put("poolId", poolId);
        payload.put("operation", op.code);
        payload.put("submittedCount", submitted);
        payload.put("successCount", success);
        payload.put("failCount", fail);
        sseService.publishTenantUserCustomEvent(tenantId, operatorUserId,
                PoolCustomerBatchConstants.SSE_POOL_BATCH_BY_CONDITION_DONE, payload);
    }

    private MessageDetailDTO buildOperatorMessageDetail(String taskId, String orgId) {
        MessageDetailDTO messageDetailDTO = new MessageDetailDTO();
        messageDetailDTO.setId(taskId);
        messageDetailDTO.setEvent(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED);
        messageDetailDTO.setTaskType(NotificationConstants.Module.CUSTOMER);
        messageDetailDTO.setOrganizationId(orgId);
        messageDetailDTO.setSysEnable(true);
        return messageDetailDTO;
    }

    private NoticeModel buildOperatorNoticeModel(String operatorUserId, String orgId, String receiverId, String resourceName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("organizationId", orgId);
        paramMap.put("name", resourceName);
        return NoticeModel.builder()
                .operator(operatorUserId)
                .event(NotificationConstants.Event.HIGH_SEAS_CUSTOMER_DISTRIBUTED)
                .paramMap(paramMap)
                .receivers(List.of(new Receiver(receiverId, NotificationConstants.Type.SYSTEM_NOTICE.name())))
                .excludeSelf(true)
                .build();
    }


    private void releaseLock(RLock lock, String lockKey, long lockThreadId) {
        try {
            if (lock != null && lock.isHeldByThread(lockThreadId)) {
                lock.unlockAsync(lockThreadId).get();
            }
        } catch (Exception ex) {
            log.warn("[POOL_BATCH_UNLOCK_FAILED] lockKey={}", lockKey, ex);
        }
    }

    private String tenantRedisKey(String rawKey) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.isNotBlank(tenantId) ? tenantId + ":" + rawKey : rawKey;
    }
}
