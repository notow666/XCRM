package cn.cordys.config;


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
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        executor.setThreadNamePrefix("custom-async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }

    /**
     * 捕获 @Async void 方法未处理的异常
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("异步任务异常: {}", method.getName() + " - " + ex.getMessage());
    }

    /**
     * MDC 上下文传递装饰器
     */
    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            // 获取父线程的 MDC 上下文
            Map<String, String> parentMdcContext = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    // 设置子线程的 MDC 上下文
                    if (parentMdcContext != null) {
                        MDC.setContextMap(parentMdcContext);
                    }

                    // 传递租户上下文（如果使用了 ThreadLocal）
                    String tenantId = TenantContext.getTenantId();
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }

                    // 执行实际任务
                    runnable.run();

                } finally {
                    // 清理 MDC 和租户上下文，防止内存泄漏
                    MDC.clear();
                    TenantContext.clear();
                }
            };
        }
    }
}