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

import java.util.Map;
import java.util.concurrent.*;

@EnableAsync
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    // 核心线程数
    private static final int CORE_POOL_SIZE = 20;
    // 最大线程数
    private static final int MAX_POOL_SIZE = 20;
    // 空闲线程最大存活秒数
    private static final int KEEP_ALIVE_SECONDS = 60;
    // 关闭时最大等待秒数
    private static final int AWAIT_TERMINATION_SECONDS = 60;

    // 同时暴露默认名称，便于 @Async 自动装配
    @Bean(name = {"threadPoolTaskExecutor", "applicationTaskExecutor"})
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("main-async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("default-async-task-");
        // 设置 MDC 传递装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean("callbackMainTaskExecutor")
    public ExecutorService callbackMainTaskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("callback-main-async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackStreamTaskExecutor")
    public ExecutorService callbackStreamTaskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("callback-stream-async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("callbackConsumerTaskExecutor")
    public ExecutorService callbackConsumerTaskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("callback-consumer-async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor.getThreadPoolExecutor();
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
                    if (tenantId != null) {
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