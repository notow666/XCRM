package cn.cordys.common.transaction;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.context.TenantContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;

import java.util.Collections;
import java.util.function.Supplier;

/**
 * 多租户场景下的编程式「独立事务」执行器（{@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}，
 * 任意 {@link Exception} 回滚）。
 *
 * 与动态租户路由数据源配合：在 {@code getConnection()} 时读取
 * {@link TenantContext#getTenantId()} 路由数据源，因此必须在开启事务之前绑定租户。
 *
 *
 * 不变量（必须遵守）
 *   先绑定租户，再开事务：{@link TenantContext#setTenantId} 或 {@link cn.cordys.common.context.ContextPropagation} 恢复，早于本类开启事务。
 *   单事务内禁止切换 tenantId：一批次只操作一个租户库。
 *   一线程一事务：并行任务各自调用 {@link #executeInNewTransaction(Supplier)}，不可跨线程共享同一 Spring 事务。
 *   异步走已包装线程池：{@link ExecutorBeanNames#MAIN_ASYNC}、
 *       {@link ExecutorBeanNames#PARALLEL}、{@link ExecutorBeanNames#BATCH} 经
 *       {@link cn.cordys.common.context.ContextPropagatingExecutor} 传播租户；禁止
 *       {@code ForkJoinPool.commonPool()} 或未包装线程池。
 *   定时 / 跨租户入口显式传 tenantId：使用
 *       {@link #executeInNewTransaction(String, Supplier)} 或先 {@link #runWithTenant(String, Supplier)}。
 *   禁止异步任务最外层大 {@code @Transactional}：否则无法实现按批提交。
 *
 * @see cn.cordys.common.context.ContextPropagatingExecutor
 * @see cn.cordys.common.constants.ExecutorBeanNames#BATCH
 */
@Component
public class TenantTransactionExecutor {

    private final PlatformTransactionManager transactionManager;
    private final RuleBasedTransactionAttribute newTransactionAttribute;

    public TenantTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.newTransactionAttribute = new RuleBasedTransactionAttribute();
        this.newTransactionAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newTransactionAttribute.setRollbackRules(Collections.singletonList(new RollbackRuleAttribute(Exception.class)));
    }

    /**
     * 使用当前线程已绑定的租户，在独立事务中执行（异步 worker 最常见）。
     */
    public void executeInNewTransaction(Runnable work) {
        executeInNewTransaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * 使用当前线程已绑定的租户，在独立事务中执行并返回结果。
     */
    public <T> T executeInNewTransaction(Supplier<T> work) {
        TenantContext.requireTenantId();
        return executeInNewTransactionInternal(work);
    }

    /**
     * 绑定指定租户后在独立事务中执行（定时任务、按租户循环等无 HTTP 上下文场景）。
     */
    public void executeInNewTransaction(String tenantId, Runnable work) {
        executeInNewTransaction(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * 绑定指定租户后在独立事务中执行并返回结果；执行后恢复调用前的租户绑定。
     */
    public <T> T executeInNewTransaction(String tenantId, Supplier<T> work) {
        return runWithTenant(tenantId, () -> executeInNewTransactionInternal(work));
    }

    /**
     * 仅绑定租户上下文，不开启事务（纯读或非 Spring 管理的资源）。
     */
    public void runWithTenant(String tenantId, Runnable work) {
        runWithTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * 仅绑定租户上下文，不开启事务；执行后恢复调用前的租户绑定。
     */
    public <T> T runWithTenant(String tenantId, Supplier<T> work) {
        requireTenantIdArgument(tenantId);
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return work.get();
        } finally {
            restoreTenant(previous);
        }
    }

    private static void requireTenantIdArgument(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    private static void restoreTenant(String previous) {
        if (StringUtils.isBlank(previous)) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(previous);
        }
    }

    private <T> T executeInNewTransactionInternal(Supplier<T> work) {
        TransactionStatus status = transactionManager.getTransaction(newTransactionAttribute);
        try {
            T result = work.get();
            transactionManager.commit(status);
            return result;
        } catch (Exception | Error ex) {
            transactionManager.rollback(status);
            throw ex;
        }
    }
}
