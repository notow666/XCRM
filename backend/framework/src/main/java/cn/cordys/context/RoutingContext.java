package cn.cordys.context;

import org.apache.commons.lang3.StringUtils;

/**
 * 当前线程 JDBC 路由用途（与 {@link TenantContext} 配合使用）。
 */
public final class RoutingContext {

    private static final ThreadLocal<RoutingPurpose> PURPOSE = new ThreadLocal<>();

    private RoutingContext() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static RoutingPurpose getPurpose() {
        return PURPOSE.get();
    }

    public static RoutingPurpose getPurposeOrDefault() {
        RoutingPurpose purpose = PURPOSE.get();
        return purpose != null ? purpose : RoutingPurpose.USER_REQUEST;
    }

    public static void setPurpose(RoutingPurpose purpose) {
        if (purpose == null) {
            PURPOSE.remove();
            return;
        }
        PURPOSE.set(purpose);
    }

    public static void clear() {
        PURPOSE.remove();
    }

    public static void runWithPurpose(RoutingPurpose purpose, Runnable runnable) {
        RoutingPurpose previous = getPurpose();
        try {
            setPurpose(purpose);
            runnable.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                setPurpose(previous);
            }
        }
    }

    public static <T> T callWithPurpose(RoutingPurpose purpose, java.util.concurrent.Callable<T> callable) throws Exception {
        RoutingPurpose previous = getPurpose();
        try {
            setPurpose(purpose);
            return callable.call();
        } finally {
            if (previous == null) {
                clear();
            } else {
                setPurpose(previous);
            }
        }
    }

    public static boolean isPrimaryOnlyPurpose(RoutingPurpose purpose) {
        if (purpose == null) {
            return false;
        }
        return switch (purpose) {
            case IDENTITY_PRIMARY, INTEGRATION_PRIMARY, EXTERNAL_API_PRIMARY,
                 DATA_SPECIALIST_PRIMARY, PRODUCTION_MAINTAIN -> true;
            default -> false;
        };
    }

    public static String describePurpose(RoutingPurpose purpose) {
        return purpose == null ? StringUtils.EMPTY : purpose.name();
    }
}
