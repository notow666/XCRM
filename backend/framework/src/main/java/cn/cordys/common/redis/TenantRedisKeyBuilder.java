package cn.cordys.common.redis;

import cn.cordys.context.TenantContext;
import org.apache.commons.lang3.StringUtils;

public final class TenantRedisKeyBuilder {

    private TenantRedisKeyBuilder() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static String tenantKey(String rawKey) {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.isBlank(rawKey)) {
            return tenantId + ":";
        }
        String prefix = tenantId + ":";
        if (rawKey.startsWith(prefix)) {
            return rawKey;
        }
        return prefix + rawKey;
    }

    public static String tenantPattern(String rawPattern) {
        return tenantKey(rawPattern);
    }
}
