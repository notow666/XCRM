package cn.cordys.common.util;

import cn.cordys.context.TenantContext;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异步工具类 - 支持 MDC 和租户上下文传递
 */
public class AsyncUtils {

    /**
     * 带上下文的 CompletableFuture.supplyAsync
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        // 保存父线程上下文
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        String tenantId = TenantContext.getTenantId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 恢复 MDC 上下文
                if (parentMdc != null) {
                    MDC.setContextMap(parentMdc);
                }
                // 恢复租户上下文
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
                return supplier.get();
            } finally {
                // 清理上下文
                MDC.clear();
                TenantContext.clear();
            }
        }, executor);
    }

    /**
     * 带上下文的 CompletableFuture.runAsync
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        String tenantId = TenantContext.getTenantId();

        return CompletableFuture.runAsync(() -> {
            try {
                if (parentMdc != null) {
                    MDC.setContextMap(parentMdc);
                }
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
                runnable.run();
            } finally {
                MDC.clear();
                TenantContext.clear();
            }
        }, executor);
    }


    /**
     * 批量异步任务（带上下文）
     */
    public static <T, R> List<Future<R>> submitAll(List<T> tasks,
                                                   Function<T, R> function,
                                                   Executor executor) {
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        String tenantId = TenantContext.getTenantId();

        List<Future<R>> futures = new ArrayList<>();
        for (T task : tasks) {
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if (parentMdc != null) {
                        MDC.setContextMap(parentMdc);
                    }
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    return function.apply(task);
                } finally {
                    MDC.clear();
                    TenantContext.clear();
                }
            }, executor);
            futures.add(future);
        }
        return futures;
    }

    /**
     * 批量异步任务（带上下文和索引）
     */
    public static <T, R> List<Future<Pair<Integer, R>>> submitAllWithIndex(List<T> tasks,
                                                                           Function<T, R> function,
                                                                           Executor executor) {
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        String tenantId = TenantContext.getTenantId();

        List<Future<Pair<Integer, R>>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i;
            final T task = tasks.get(i);

            CompletableFuture<Pair<Integer, R>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if (parentMdc != null) {
                        MDC.setContextMap(parentMdc);
                    }
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    R result = function.apply(task);
                    return Pair.of(index, result);
                } finally {
                    MDC.clear();
                    TenantContext.clear();
                }
            }, executor);
            futures.add(future);
        }
        return futures;
    }

    /**
     * 等待所有任务完成
     */
    public static void waitAll(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException("异步任务执行失败", e);
            }
        }
    }

    /**
     * 等待所有任务完成（带超时）
     */
    public static boolean waitAll(List<? extends Future<?>> futures, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        for (Future<?> future : futures) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            try {
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new RuntimeException("异步任务执行失败", e);
            }
        }
        return true;
    }

    /**
     * Pair 简单类
     */
    public static class Pair<L, R> {
        private final L left;
        private final R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }

        public L getLeft() {
            return left;
        }

        public R getRight() {
            return right;
        }
    }
}