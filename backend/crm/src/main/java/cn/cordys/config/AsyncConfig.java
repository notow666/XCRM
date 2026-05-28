package cn.cordys.config;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.constants.MdcConstants;
import cn.cordys.common.context.ContextPropagatingExecutor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /** @Async、站内通知、操作日志等通用后台任务 */
    private static final int CORE_POOL_SIZE = 40;
    private static final int MAX_POOL_SIZE = 240;
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final int AWAIT_TERMINATION_SECONDS = 60;
    private static final int QUEUE_CAPACITY = 1000;

    /** 单次请求内短时并行（统计、导出行构建、附件复制） */
    private static final int PARALLEL_CORE_POOL_SIZE = 16;
    private static final int PARALLEL_MAX_POOL_SIZE = 64;
    private static final int PARALLEL_QUEUE_CAPACITY = 2048;

    /**
     * 多租户批量任务专用：导入、按条件批量分配/删除、异步导出、公海线索分发、租户开通等。
     * <p>并发 deliberately 低于主池，避免多租户同时跑大批量时打满 DB；队列略大以承接排队任务。</p>
     */
    private static final int BATCH_CORE_POOL_SIZE = 128;
    private static final int BATCH_MAX_POOL_SIZE = 512;
    private static final int BATCH_QUEUE_CAPACITY = 2048;
    private static final int BATCH_KEEP_ALIVE_SECONDS = 120;

    /** MMBA Stream 单条消息消费（与 cordys.mmba.callback.db-concurrency 同量级） */
    private static final int CALLBACK_CONSUMER_CORE_POOL_SIZE = 4;
    private static final int CALLBACK_CONSUMER_MAX_POOL_SIZE = 8;
    private static final int CALLBACK_CONSUMER_QUEUE_CAPACITY = 256;

    @Bean(name = {ExecutorBeanNames.MAIN_ASYNC, ExecutorBeanNames.APPLICATION_TASK})
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "main-async-task-",
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY,
                KEEP_ALIVE_SECONDS);
        executor.initialize();
        return ContextPropagatingExecutor.wrap(executor);
    }

    @Bean(name = ExecutorBeanNames.PARALLEL)
    public Executor parallelTaskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "parallel-task-",
                PARALLEL_CORE_POOL_SIZE,
                PARALLEL_MAX_POOL_SIZE,
                PARALLEL_QUEUE_CAPACITY,
                KEEP_ALIVE_SECONDS);
        executor.initialize();
        return ContextPropagatingExecutor.wrap(executor);
    }

    @Bean(name = ExecutorBeanNames.BATCH)
    public Executor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "batch-task-",
                BATCH_CORE_POOL_SIZE,
                BATCH_MAX_POOL_SIZE,
                BATCH_QUEUE_CAPACITY,
                BATCH_KEEP_ALIVE_SECONDS);
        executor.initialize();
        return ContextPropagatingExecutor.wrap(executor);
    }

    @Bean("callbackMainTaskExecutor")
    public ExecutorService callbackMainTaskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "callback-main-async-task-",
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY,
                KEEP_ALIVE_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackStreamTaskExecutor")
    public ExecutorService callbackStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "callback-stream-async-task-",
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY,
                KEEP_ALIVE_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackConsumerTaskExecutor")
    public ExecutorService callbackConsumerTaskExecutor() {
        ThreadPoolTaskExecutor executor = createExecutor(
                "callback-consumer-async-task-",
                CALLBACK_CONSUMER_CORE_POOL_SIZE,
                CALLBACK_CONSUMER_MAX_POOL_SIZE,
                CALLBACK_CONSUMER_QUEUE_CAPACITY,
                KEEP_ALIVE_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    private ThreadPoolTaskExecutor createExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize,
                                                    int queueCapacity, int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.setQueueCapacity(queueCapacity);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            String traceId = copyOfContextMap != null ? copyOfContextMap.get(MdcConstants.TRACE_ID_KEY) : "";
            String tenantId = copyOfContextMap != null ? copyOfContextMap.get(MdcConstants.TENANT_ID_KEY) : "";
            log.error(String.format(
                    "异步方法执行异常 - 方法: %s, traceId: %s, 租户: %s, 异常: %s",
                    method.getDeclaringClass().getName() + "-" + method.getName(),
                    traceId,
                    tenantId,
                    throwable.getMessage()
            ));
        };
    }
}
