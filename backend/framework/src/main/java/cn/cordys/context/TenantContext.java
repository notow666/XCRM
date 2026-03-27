package cn.cordys.context;

import org.apache.commons.lang3.StringUtils;

public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "default";
    private static final ThreadLocal<String> TENANT_ID = new InheritableThreadLocal<>();

    private TenantContext() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static String getTenantIdOrDefault() {
        String tenantId = TENANT_ID.get();
        return StringUtils.isBlank(tenantId) ? DEFAULT_TENANT_ID : tenantId;
    }

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

