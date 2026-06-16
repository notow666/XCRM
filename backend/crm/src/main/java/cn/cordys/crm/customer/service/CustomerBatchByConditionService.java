package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.transaction.TenantTransactionExecutor;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.customer.constants.CustomerBatchConstants;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.request.CustomerBatchTransferByConditionRequest;
import cn.cordys.crm.customer.dto.request.CustomerBatchTransferRequest;
import cn.cordys.crm.customer.dto.request.CustomerBatchToPoolByConditionRequest;
import cn.cordys.crm.customer.dto.request.CustomerBatchUpdateByConditionRequest;
import cn.cordys.crm.customer.dto.request.CustomerPageRequest;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.dto.MessageDetailDTO;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.request.BatchPoolReasonRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.crm.system.dto.response.BatchAffectResponse;
import cn.cordys.crm.system.notice.common.NoticeModel;
import cn.cordys.crm.system.notice.common.Receiver;
import cn.cordys.crm.system.notice.sender.insite.InSiteNoticeSender;
import cn.cordys.crm.system.notice.sse.SseService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static cn.cordys.crm.customer.service.CustomerBatchTypes.*;

@Slf4j
@Service
public class CustomerBatchByConditionService {

    private static final int BATCH_DELETE_BY_CONDITION_PAGE_SIZE = 2000;
    private static final int BATCH_TRANSFER_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_TO_POOL_BY_CONDITION_MAX_SIZE = 2000;
    private static final int BATCH_UPDATE_BY_CONDITION_MAX_SIZE = 2000;
    private static final int CHUNK_SIZE = 200;
    private static final String BATCH_CUSTOMER_OPERATION_LOCK_PREFIX = "crm:customer:batch-op:";

    private enum Op {
        DELETE("DELETE", "批量删除"),
        TRANSFER("TRANSFER", "批量转移"),
        TO_POOL("TO_POOL", "批量移入公海"),
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
    private CustomerBatchSupport batchSupport;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private CustomerFieldService customerFieldService;
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

    public Map<String, Object> batchDeleteByCondition(CustomerPageRequest request, String userId, String orgId,
                                                      DeptDataPermissionDTO deptDataPermission) {
        CustomerPageRequest asyncRequest = BeanUtils.copyBean(new CustomerPageRequest(), request);
        return submit(orgId, userId, Op.DELETE, "批量删除任务已提交",
                (taskId, tenantId) ->
                        doBatchDeleteByConditionAsync(taskId, tenantId, asyncRequest, userId, orgId, deptDataPermission));
    }

    public Map<String, Object> batchTransferByCondition(CustomerBatchTransferByConditionRequest request, String userId,
                                                        String orgId, DeptDataPermissionDTO deptDataPermission) {
        normalizeTransferOwnerUserIds(request);
        if (request.getTransferCount() == null || request.getTransferCount() < 1
                || CollectionUtils.isEmpty(request.getOwnerUserIds())) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerBatchTransferByConditionRequest asyncRequest =
                BeanUtils.copyBean(new CustomerBatchTransferByConditionRequest(), request);
        return submit(orgId, userId, Op.TRANSFER, "批量转移任务已提交",
                (taskId, tenantId) ->
                        doBatchTransferByConditionAsync(taskId, tenantId, asyncRequest, userId, orgId, deptDataPermission));
    }

    public Map<String, Object> batchToPoolByCondition(CustomerBatchToPoolByConditionRequest request, String userId,
                                                      String orgId, DeptDataPermissionDTO deptDataPermission) {
        if (request.getToPoolCount() == null || request.getToPoolCount() < 1) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerBatchToPoolByConditionRequest asyncRequest =
                BeanUtils.copyBean(new CustomerBatchToPoolByConditionRequest(), request);
        return submit(orgId, userId, Op.TO_POOL, "批量移入公海任务已提交",
                (taskId, tenantId) ->
                        doBatchToPoolByConditionAsync(taskId, tenantId, asyncRequest, userId, orgId, deptDataPermission));
    }

    public Map<String, Object> batchUpdateByCondition(CustomerBatchUpdateByConditionRequest request, String userId,
                                                      String orgId, DeptDataPermissionDTO deptDataPermission) {
        BaseField field = customerFieldService.getAndCheckField(request.getFieldId(), orgId);
        int updateCount = Math.min(request.getUpdateCount(), BATCH_UPDATE_BY_CONDITION_MAX_SIZE);
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey()) && updateCount > 1) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }
        if (field.needRepeatCheck() && updateCount > 1 && request.getFieldValue() != null
                && StringUtils.isNotBlank(String.valueOf(request.getFieldValue()))) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
        }
        CustomerBatchUpdateByConditionRequest asyncRequest =
                BeanUtils.copyBean(new CustomerBatchUpdateByConditionRequest(), request);
        return submit(orgId, userId, Op.UPDATE, "批量编辑任务已提交",
                (taskId, tenantId) ->
                        doBatchUpdateByConditionAsync(taskId, tenantId, asyncRequest, field, userId, orgId, deptDataPermission));
    }

    private void doBatchDeleteByConditionAsync(String taskId, String tenantId, CustomerPageRequest request,
                                               String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        tenantTransactionExecutor.runWithTenant(tenantId, () ->
                executeBatchDeleteByCondition(taskId, tenantId, request, userId, orgId, deptDataPermission));
    }

    private void executeBatchDeleteByCondition(String taskId, String tenantId, CustomerPageRequest request,
                                               String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.DELETE;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        try {
            log.info("[CUSTOMER_BATCH_DELETE_START] taskId={}, orgId={}, operator={}", taskId, orgId, userId);
            long collectStart = System.currentTimeMillis();
            List<String> matchedIds = collectIdsUnlimited(request, userId, orgId, deptDataPermission);
            submittedCount = matchedIds.size();
            log.info("[CUSTOMER_BATCH_DELETE_COLLECT_COST] taskId={}, costMs={}, submittedCount={}",
                    taskId, System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(matchedIds)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), 0, 0, 0, "未查询到可删除客户");
                return;
            }

            BatchChunkResult chunkResult = runParallelIdChunks(tenantId, taskId, op.code,
                    batchSupport.partition(matchedIds, CHUNK_SIZE),
                    chunkIds -> {
                        batchSupport.batchDelete(chunkIds, userId, orgId);
                        return chunkIds.size();
                    });
            successCount = chunkResult.successCount();
            failCount = chunkResult.failCount();

            log.info("[CUSTOMER_BATCH_DELETE_FINISH] taskId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            failCount = Math.max(failCount, submittedCount - successCount);
            log.error("[CUSTOMER_BATCH_DELETE_FAILED] taskId={}, operator={}, submittedCount={}, successCount={}, failCount={}",
                    taskId, userId, submittedCount, successCount, failCount, ex);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount, failCount, ex.getMessage());
        }
    }

    private void doBatchTransferByConditionAsync(String taskId, String tenantId,
                                                 CustomerBatchTransferByConditionRequest request, String userId,
                                                 String orgId, DeptDataPermissionDTO deptDataPermission) {
        tenantTransactionExecutor.runWithTenant(tenantId, () ->
                executeBatchTransferByCondition(taskId, tenantId, request, userId, orgId, deptDataPermission));
    }

    private void executeBatchTransferByCondition(String taskId, String tenantId,
                                                 CustomerBatchTransferByConditionRequest request, String userId,
                                                 String orgId, DeptDataPermissionDTO deptDataPermission) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.TRANSFER;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        try {
            normalizeTransferOwnerUserIds(request);
            int transferCount = Math.min(request.getTransferCount(), BATCH_TRANSFER_BY_CONDITION_MAX_SIZE);
            log.info("[CUSTOMER_BATCH_TRANSFER_START] taskId={}, orgId={}, operator={}, ownerUserIds={}",
                    taskId, orgId, userId, request.getOwnerUserIds());
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, orgId, userId, transferCount, deptDataPermission);
            submittedCount = ids.size();
            log.info("[CUSTOMER_BATCH_TRANSFER_COLLECT_COST] taskId={}, costMs={}, submittedCount={}",
                    taskId, System.currentTimeMillis() - collectStart, submittedCount);
            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), 0, 0, 0, "未查询到可转移客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> originCustomers = filterPrivateCandidates(customerMapper.selectByIds(ids), requestOrderMap);
            CustomerBatchTransferPlan plan = batchSupport.buildBatchTransferPlan(request, originCustomers, userId, orgId);
            failCount = plan.skippedCount() + (submittedCount - originCustomers.size());
            if (plan.ownerToCustomerIds().isEmpty()) {
                log.warn("[CUSTOMER_BATCH_TRANSFER_PLAN_EMPTY] taskId={}, submittedCount={}, skippedCount={}",
                        taskId, submittedCount, plan.skippedCount());
            } else {
                BatchChunkResult transferResult = runParallelTransferOwnerChunks(tenantId, taskId, plan, userId, orgId);
                successCount = transferResult.successCount();
                failCount += transferResult.failCount();
            }

            log.info("[CUSTOMER_BATCH_TRANSFER_FINISH] taskId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            log.error("[CUSTOMER_BATCH_TRANSFER_FAILED] taskId={}, operator={}", taskId, userId, ex);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private void doBatchToPoolByConditionAsync(String taskId, String tenantId,
                                               CustomerBatchToPoolByConditionRequest request, String userId,
                                               String orgId, DeptDataPermissionDTO deptDataPermission) {
        tenantTransactionExecutor.runWithTenant(tenantId, () ->
                executeBatchToPoolByCondition(taskId, tenantId, request, userId, orgId, deptDataPermission));
    }

    private void executeBatchToPoolByCondition(String taskId, String tenantId,
                                               CustomerBatchToPoolByConditionRequest request, String userId,
                                               String orgId, DeptDataPermissionDTO deptDataPermission) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.TO_POOL;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        try {
            int toPoolCount = Math.min(request.getToPoolCount(), BATCH_TO_POOL_BY_CONDITION_MAX_SIZE);
            log.info("[CUSTOMER_BATCH_TO_POOL_START] taskId={}, orgId={}, operator={}", taskId, orgId, userId);
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, orgId, userId, toPoolCount, deptDataPermission);
            submittedCount = ids.size();
            log.info("[CUSTOMER_BATCH_TO_POOL_COLLECT_COST] taskId={}, costMs={}, submittedCount={}",
                    taskId, System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), 0, 0, 0, "未查询到可移入公海客户");
                return;
            }

            BatchChunkResult chunkResult = runParallelIdChunks(tenantId, taskId, op.code,
                    batchSupport.partition(ids, CHUNK_SIZE),
                    chunkIds -> {
                        BatchPoolReasonRequest batchRequest = new BatchPoolReasonRequest();
                        batchRequest.setIds(chunkIds);
                        batchRequest.setPoolId(request.getTargetPoolId());
                        batchRequest.setReasonId(request.getReasonId());
                        BatchAffectResponse response = requireCustomerService().batchToPool(batchRequest, userId, orgId);
                        return response.getSuccess() == null ? 0 : response.getSuccess();
                    });
            successCount = chunkResult.successCount();
            failCount = submittedCount - successCount;

            log.info("[CUSTOMER_BATCH_TO_POOL_FINISH] taskId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            log.error("[CUSTOMER_BATCH_TO_POOL_FAILED] taskId={}, operator={}", taskId, userId, ex);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private void doBatchUpdateByConditionAsync(String taskId, String tenantId,
                                               CustomerBatchUpdateByConditionRequest request, BaseField field,
                                               String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        tenantTransactionExecutor.runWithTenant(tenantId, () ->
                executeBatchUpdateByCondition(taskId, tenantId, request, field, userId, orgId, deptDataPermission));
    }

    private void executeBatchUpdateByCondition(String taskId, String tenantId,
                                               CustomerBatchUpdateByConditionRequest request, BaseField field,
                                               String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        long totalStart = System.currentTimeMillis();
        Op op = Op.UPDATE;
        int submittedCount = 0;
        int successCount = 0;
        int failCount = 0;
        boolean mobileBatchUpdate = Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey());
        try {
            int updateCount = Math.min(request.getUpdateCount(), BATCH_UPDATE_BY_CONDITION_MAX_SIZE);
            log.info("[CUSTOMER_BATCH_UPDATE_START] taskId={}, operator={}, fieldId={}", taskId, userId, request.getFieldId());
            long collectStart = System.currentTimeMillis();
            List<String> ids = collectIdsLimited(request, orgId, userId, updateCount, deptDataPermission);
            submittedCount = ids.size();
            log.info("[CUSTOMER_BATCH_UPDATE_COLLECT_COST] taskId={}, costMs={}, submittedCount={}",
                    taskId, System.currentTimeMillis() - collectStart, submittedCount);

            if (CollectionUtils.isEmpty(ids)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), 0, 0, 0, "未查询到可编辑客户");
                return;
            }

            Map<String, Integer> requestOrderMap = buildRequestOrderMap(ids);
            List<Customer> customers = customerMapper.selectByIds(ids);
            List<Customer> candidates = filterPrivateCandidates(customers, requestOrderMap);
            failCount = submittedCount - candidates.size();

            if (CollectionUtils.isEmpty(candidates)) {
                sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, 0, failCount, null);
                return;
            }

            ResourceBatchEditRequest batchEditRequest = new ResourceBatchEditRequest();
            batchEditRequest.setFieldId(request.getFieldId());
            batchEditRequest.setFieldValue(request.getFieldValue());

            if (mobileBatchUpdate) {
                if (candidates.size() > 1) {
                    throw new GenericException(Translator.getWithArgs("common.field_value.repeat", field.getName()));
                }
                batchEditRequest.setIds(List.of(candidates.getFirst().getId()));
                tenantTransactionExecutor.executeInNewTransaction(tenantId, () ->
                        batchSupport.executeCustomerBatchUpdate(batchEditRequest, candidates, field, userId, orgId));
                successCount = 1;
            } else {
                List<List<Customer>> chunks = partitionCustomers(candidates, CHUNK_SIZE);
                AtomicInteger updateFail = new AtomicInteger(0);
                runParallelCustomerChunks(tenantId, taskId, op.code, chunks, chunk -> {
                    ResourceBatchEditRequest chunkRequest = new ResourceBatchEditRequest();
                    chunkRequest.setIds(chunk.stream().map(Customer::getId).toList());
                    chunkRequest.setFieldId(request.getFieldId());
                    chunkRequest.setFieldValue(request.getFieldValue());
                    batchSupport.executeCustomerBatchUpdate(chunkRequest, chunk, field, userId, orgId);
                }, updateFail);
                successCount = candidates.size() - updateFail.get();
                failCount += updateFail.get();
            }

            log.info("[CUSTOMER_BATCH_UPDATE_FINISH] taskId={}, submittedCount={}, successCount={}, failCount={}, totalCostMs={}",
                    taskId, submittedCount, successCount, failCount, System.currentTimeMillis() - totalStart);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount, failCount, null);
        } catch (Exception ex) {
            log.error("[CUSTOMER_BATCH_UPDATE_FAILED] taskId={}, operator={}, fieldId={}", taskId, userId, request.getFieldId(), ex);
            sendOperatorNotice(taskId, tenantId, orgId, userId, op, request.getViewId(), submittedCount, successCount,
                    Math.max(failCount, submittedCount - successCount), ex.getMessage());
        }
    }

    private Map<String, Object> submit(String orgId, String operator, Op op, String acceptedMessage, AsyncTask task) {
        String taskId = IDGenerator.nextStr();
        long lockThreadId = Long.parseLong(taskId);
        String lockKey = tenantRedisKey(BATCH_CUSTOMER_OPERATION_LOCK_PREFIX + orgId);
        RLock lock = redisson.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, lockThreadId).get();
        } catch (Exception ex) {
            throw new GenericException(op.label + "加锁失败");
        }
        if (!locked) {
            log.info("[CUSTOMER_BATCH_{}_LOCK_REJECTED] orgId={}, operator={}, lockKey={}", op.code, orgId, operator, lockKey);
            return Map.of(
                    "accepted", false,
                    "taskId", "",
                    "message", "当前组织已有客户批量任务执行中，请稍后再试"
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
        log.info("[CUSTOMER_BATCH_{}_SUBMIT] taskId={}, orgId={}, operator={}", op.code, taskId, orgId, operator);
        return Map.of(
                "accepted", true,
                "taskId", taskId,
                "message", acceptedMessage
        );
    }

    private List<String> collectIdsLimited(CustomerPageRequest request, String orgId, String userId, int maxSize,
                                           DeptDataPermissionDTO deptDataPermission) {
        if (maxSize <= 0) {
            return List.of();
        }
        PageHelper.startPage(1, maxSize, false);
        List<String> ids = extCustomerMapper.listIds(request, orgId, userId, deptDataPermission);
        return CollectionUtils.isEmpty(ids) ? List.of() : new ArrayList<>(ids);
    }

    private List<String> collectIdsUnlimited(CustomerPageRequest request, String userId, String orgId,
                                             DeptDataPermissionDTO deptDataPermission) {
        List<String> matchedIds = new ArrayList<>();
        int pageNum = 1;
        PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_PAGE_SIZE, false);
        List<String> pageIds = extCustomerMapper.listIds(request, orgId, userId, deptDataPermission);
        while (CollectionUtils.isNotEmpty(pageIds)) {
            matchedIds.addAll(pageIds);
            if (pageIds.size() < BATCH_DELETE_BY_CONDITION_PAGE_SIZE) {
                return matchedIds;
            }
            pageNum++;
            PageHelper.startPage(pageNum, BATCH_DELETE_BY_CONDITION_PAGE_SIZE, false);
            pageIds = extCustomerMapper.listIds(request, orgId, userId, deptDataPermission);
        }
        return matchedIds;
    }

    /**
     * 按目标负责人并行、同一负责人内按 200 条顺序分块落库，避免库容并发冲突。
     */
    private BatchChunkResult runParallelTransferOwnerChunks(String tenantId, String taskId,
                                                            CustomerBatchTransferPlan plan, String userId, String orgId) {
        if (plan == null || plan.ownerToCustomerIds().isEmpty()) {
            return new BatchChunkResult(0, 0);
        }
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : plan.ownerToCustomerIds().entrySet()) {
            if (CollectionUtils.isEmpty(entry.getValue())) {
                continue;
            }
            String targetOwnerId = entry.getKey();
            List<List<String>> ownerChunks = batchSupport.partition(entry.getValue(), CHUNK_SIZE);
            futures.add(CompletableFuture.runAsync(() -> {
                for (List<String> chunk : ownerChunks) {
                    if (CollectionUtils.isEmpty(chunk)) {
                        continue;
                    }
                    List<String> chunkCopy = new ArrayList<>(chunk);
                    try {
                        int chunkFail = tenantTransactionExecutor.executeInNewTransaction(tenantId, () -> {
                            CustomerBatchTransferRequest subRequest = new CustomerBatchTransferRequest();
                            subRequest.setIds(chunkCopy);
                            subRequest.setOwner(targetOwnerId);
                            return requireCustomerService().batchTransferSingleOwner(subRequest, userId, orgId);
                        });
                        successCount.addAndGet(chunkCopy.size() - chunkFail);
                        failCount.addAndGet(chunkFail);
                    } catch (Exception ex) {
                        failCount.addAndGet(chunkCopy.size());
                        log.warn("[CUSTOMER_BATCH_TRANSFER_CHUNK_FAILED] taskId={}, ownerId={}, chunkSize={}",
                                taskId, targetOwnerId, chunkCopy.size(), ex);
                    }
                }
            }, batchExecutor));
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        return new BatchChunkResult(successCount.get(), failCount.get());
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
                } catch (Exception ex) {
                    failCount.addAndGet(chunkCopy.size());
                    log.warn("[CUSTOMER_BATCH_CHUNK_FAILED] taskId={}, operation={}, chunkSize={}",
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
                } catch (Exception ex) {
                    failCounter.addAndGet(chunkCopy.size());
                    log.warn("[CUSTOMER_BATCH_CHUNK_FAILED] taskId={}, operation={}, chunkSize={}",
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

    private List<Customer> filterPrivateCandidates(List<Customer> customers) {
        return customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> !Boolean.TRUE.equals(customer.getInSharedPool()))
                .toList();
    }

    private List<Customer> filterPrivateCandidates(List<Customer> customers, Map<String, Integer> requestOrderMap) {
        return customers.stream()
                .filter(Objects::nonNull)
                .filter(customer -> !Boolean.TRUE.equals(customer.getInSharedPool()))
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

    private void sendOperatorNotice(String taskId, String tenantId, String orgId, String operatorUserId, Op op,
                                    String viewId, int submitted, int success, int fail, String extraMessage) {
        String subjectText = "客户" + op.label + "通知";
        String resourceName = "客户列表" + op.label + "任务完成";
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
        sendCustomerBatchByConditionDoneSse(tenantId, operatorUserId, op, viewId, submitted, success, fail);
        log.info("[CUSTOMER_BATCH_{}_OPERATOR_NOTICE] taskId={}, submitted={}, success={}, fail={}",
                op.code, taskId, submitted, success, fail);
    }

    private void sendCustomerBatchByConditionDoneSse(String tenantId, String operatorUserId, Op op, String viewId,
                                                     int submitted, int success, int fail) {
        if (StringUtils.isAnyBlank(tenantId, operatorUserId)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", CustomerBatchConstants.SSE_CUSTOMER_BATCH_BY_CONDITION_DONE);
        payload.put("viewId", viewId);
        payload.put("operation", op.code);
        payload.put("submittedCount", submitted);
        payload.put("successCount", success);
        payload.put("failCount", fail);
        sseService.publishTenantUserCustomEvent(tenantId, operatorUserId,
                CustomerBatchConstants.SSE_CUSTOMER_BATCH_BY_CONDITION_DONE, payload);
    }

    private MessageDetailDTO buildOperatorMessageDetail(String taskId, String orgId) {
        MessageDetailDTO messageDetailDTO = new MessageDetailDTO();
        messageDetailDTO.setId(taskId);
        messageDetailDTO.setEvent(NotificationConstants.Event.CUSTOMER_IMPORT);
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
                .event(NotificationConstants.Event.CUSTOMER_IMPORT)
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
            log.warn("[CUSTOMER_BATCH_UNLOCK_FAILED] lockKey={}", lockKey, ex);
        }
    }

    private String tenantRedisKey(String rawKey) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.isNotBlank(tenantId) ? tenantId + ":" + rawKey : rawKey;
    }

    private void normalizeTransferOwnerUserIds(CustomerBatchTransferByConditionRequest request) {
        if (CollectionUtils.isNotEmpty(request.getOwnerUserIds())) {
            return;
        }
        if (StringUtils.isNotBlank(request.getOwner())) {
            request.setOwnerUserIds(List.of(request.getOwner()));
        }
    }

    private CustomerService requireCustomerService() {
        return Objects.requireNonNull(CommonBeanFactory.getBean(CustomerService.class));
    }
}
