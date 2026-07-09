package cn.cordys.common.context;

import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * MDC 与租户上下文的 capture / restore / clear（单一实现，供 Executor 装饰器使用）。
 */
public final class ContextPropagation {

    private ContextPropagation() {
    }

    public static Snapshot capture() {
        return new Snapshot(MDC.getCopyOfContextMap(), TenantContext.getTenantId(), RoutingContext.getPurpose());
    }

    public static Runnable wrap(Runnable runnable, Snapshot snapshot) {
        return () -> runWith(snapshot, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> Callable<T> wrap(Callable<T> callable, Snapshot snapshot) {
        return () -> runWith(snapshot, callable);
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier, Snapshot snapshot) {
        return () -> runWith(snapshot, supplier::get);
    }

    private static <T> T runWith(Snapshot snapshot, Callable<T> callable) {
        try {
            snapshot.apply();
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            MDC.clear();
            RoutingContext.clear();
            TenantContext.clear();
        }
    }

    public static final class Snapshot {
        private final Map<String, String> mdc;
        private final String tenantId;
        private final RoutingPurpose routingPurpose;

        Snapshot(Map<String, String> mdc, String tenantId, RoutingPurpose routingPurpose) {
            this.mdc = mdc;
            this.tenantId = tenantId;
            this.routingPurpose = routingPurpose;
        }

        void apply() {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            if (StringUtils.hasText(tenantId)) {
                TenantContext.setTenantId(tenantId);
            }
            if (routingPurpose != null) {
                RoutingContext.setPurpose(routingPurpose);
            } else {
                RoutingContext.clear();
            }
        }
    }
}
