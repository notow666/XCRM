package cn.cordys.common.schedule;

import org.apache.commons.lang3.StringUtils;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

/**
 * Quartz Job/Trigger 命名约定：{@code {tenantId}:{logicalName}}，用于中央 QRTZ 库中的逻辑租户隔离。
 */
public final class TenantQuartzKeys {

    private TenantQuartzKeys() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static String tenantPrefix(String tenantId) {
        return StringUtils.trimToEmpty(tenantId) + ":";
    }

    public static String appendTenantPrefix(String value, String tenantId) {
        String prefix = tenantPrefix(tenantId);
        if (StringUtils.isBlank(value) || value.startsWith(prefix)) {
            return value;
        }
        return prefix + value;
    }

    /**
     * 从带租户前缀的 Job/Trigger 名称解析 tenantId。
     */
    public static String parseTenantId(String prefixedName) {
        String name = StringUtils.trimToNull(prefixedName);
        if (name == null) {
            return null;
        }
        int colon = name.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return name.substring(0, colon);
    }

    public static String parseTenantId(JobKey jobKey) {
        if (jobKey == null) {
            return null;
        }
        String tenantId = parseTenantId(jobKey.getName());
        if (tenantId != null) {
            return tenantId;
        }
        return parseTenantId(jobKey.getGroup());
    }

    public static String parseTenantId(TriggerKey triggerKey) {
        if (triggerKey == null) {
            return null;
        }
        String tenantId = parseTenantId(triggerKey.getName());
        if (tenantId != null) {
            return tenantId;
        }
        return parseTenantId(triggerKey.getGroup());
    }
}
