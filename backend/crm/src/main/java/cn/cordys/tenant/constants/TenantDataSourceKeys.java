package cn.cordys.tenant.constants;

import org.apache.commons.lang3.StringUtils;

/**
 * 动态租户数据源路由键：主库使用 tenantId，备用库使用 tenantId@shadow。
 */
public final class TenantDataSourceKeys {

    public static final String SHADOW_SUFFIX = "@shadow";

    private TenantDataSourceKeys() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static String primary(String tenantId) {
        return StringUtils.trimToNull(tenantId);
    }

    public static String shadow(String tenantId) {
        String id = StringUtils.trimToNull(tenantId);
        if (id == null) {
            return null;
        }
        return id + SHADOW_SUFFIX;
    }

    public static boolean isShadowKey(String lookupKey) {
        return lookupKey != null && lookupKey.endsWith(SHADOW_SUFFIX);
    }

    public static String tenantIdFromKey(String lookupKey) {
        if (StringUtils.isBlank(lookupKey)) {
            return null;
        }
        if (isShadowKey(lookupKey)) {
            return lookupKey.substring(0, lookupKey.length() - SHADOW_SUFFIX.length());
        }
        return lookupKey;
    }
}
