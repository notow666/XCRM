//package cn.cordys.config;
//
//import cn.cordys.context.TenantContext;
//import org.slf4j.MDC;
//import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.task.TaskDecorator;
//import org.springframework.scheduling.annotation.AsyncConfigurer;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//
//import java.util.Map;
//import java.util.concurrent.Executor;
//
///**
// * 异步配置 - 使 @Async 注解自动传递 MDC 上下文
// */
//@Configuration
//public class AsyncConfigurerImpl implements AsyncConfigurer {
//
//    @Override
//    public Executor getAsyncExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(10);
//        executor.setMaxPoolSize(20);
//        executor.setQueueCapacity(200);
//        executor.setThreadNamePrefix("default-async-");
//
//        // 设置 MDC 传递装饰器
//        executor.setTaskDecorator(new TaskDecorator() {
//            @Override
//            public Runnable decorate(Runnable runnable) {
//                Map<String, String> parentMdc = MDC.getCopyOfContextMap();
//                String tenantId = TenantContext.getTenantId();
//
//                return () -> {
//                    try {
//                        if (parentMdc != null) {
//                            MDC.setContextMap(parentMdc);
//                        }
//                        if (tenantId != null) {
//                            TenantContext.setTenantId(tenantId);
//                        }
//                        runnable.run();
//                    } finally {
//                        MDC.clear();
//                        TenantContext.clear();
//                    }
//                };
//            }
//        });
//
//        executor.initialize();
//        return executor;
//    }
//
//    @Override
//    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
//        return (throwable, method, params) -> {
//            // 异步方法异常处理
//            String requestId = MDC.get("requestId");
//            String tenantId = MDC.get("tenantId");
//
//            System.err.println(String.format(
//                    "异步方法执行异常 - 方法: %s, 请求ID: %s, 租户: %s, 异常: %s",
//                    method.getName(), requestId, tenantId, throwable.getMessage()
//            ));
//        };
//    }
//}