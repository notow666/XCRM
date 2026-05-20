package cn.cordys.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异步工具类。MDC / 租户上下文由已包装的 {@link cn.cordys.common.context.ContextPropagatingExecutor} 传播。
 */
public class AsyncUtils {

    /**
     * CompletableFuture.supplyAsync（上下文由 executor 负责传播）
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * CompletableFuture.runAsync（上下文由 executor 负责传播）
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    /**
     * 批量异步任务
     */
    public static <T, R> List<Future<R>> submitAll(List<T> tasks,
                                                   Function<T, R> function,
                                                   Executor executor) {
        List<Future<R>> futures = new ArrayList<>();
        for (T task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> function.apply(task), executor));
        }
        return futures;
    }

    /**
     * 批量异步任务（带索引）
     */
    public static <T, R> List<Future<Pair<Integer, R>>> submitAllWithIndex(List<T> tasks,
                                                                           Function<T, R> function,
                                                                           Executor executor) {
        List<Future<Pair<Integer, R>>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i;
            final T task = tasks.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> Pair.of(index, function.apply(task)), executor));
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
