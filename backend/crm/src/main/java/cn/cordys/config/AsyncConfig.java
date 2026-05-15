package cn.cordys.config;

import cn.cordys.common.constants.MdcConstants;
import cn.cordys.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.*;

@EnableAsync
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    // 核心线程数
    private static final int CORE_POOL_SIZE = 40;
    // 最大线程数
    private static final int MAX_POOL_SIZE = 240;
    // 空闲线程最大存活秒数
    private static final int KEEP_ALIVE_SECONDS = 60;
    // 关闭时最大等待秒数
    private static final int AWAIT_TERMINATION_SECONDS = 60;

    private static final int QUEUE_CAPACITY = 1000;

    // 同时暴露默认名称，便于 @Async 自动装配
    @Bean(name = {"threadPoolTaskExecutor", "applicationTaskExecutor"})
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = defaultExecutor("main-async-task-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean("callbackMainTaskExecutor")
    public ExecutorService callbackMainTaskExecutor(){
        ThreadPoolTaskExecutor executor = defaultExecutor("callback-main-async-task-");
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackStreamTaskExecutor")
    public ExecutorService callbackStreamTaskExecutor(){
        ThreadPoolTaskExecutor executor = defaultExecutor("callback-stream-async-task-");
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackConsumerTaskExecutor")
    public ExecutorService callbackConsumerTaskExecutor(){
        ThreadPoolTaskExecutor executor = defaultExecutor("callback-consumer-async-task-");
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    private ThreadPoolTaskExecutor defaultExecutor(String threadNamePrefixSet) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix(threadNamePrefixSet);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        return executor;
    }

    /**
     * 捕获 @Async void 方法未处理的异常
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            // 异步方法异常处理
            Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            log.error(String.format(
                    "异步方法执行异常 - 方法: %s, traceId: %s, 租户: %s, 异常: %s",
                    method.getName(),
                    copyOfContextMap.get(MdcConstants.TRACE_ID_KEY),
                    copyOfContextMap.get(MdcConstants.TENANT_ID_KEY),
                    throwable.getMessage()
            ));
        };
    }

    /**
     * MDC 上下文传递装饰器
     */
    static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> parentMdc = MDC.getCopyOfContextMap();
            String tenantId = TenantContext.getTenantId();
            return () -> {
                try {
                    if (parentMdc != null) {
                        MDC.setContextMap(parentMdc);
                    }
                    if (StringUtils.hasText(tenantId)) {
                        TenantContext.setTenantId(tenantId);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    TenantContext.clear();
                }
            };
        }
    }
}