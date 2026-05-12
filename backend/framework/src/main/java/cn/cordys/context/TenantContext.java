package cn.cordys.context;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
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

    /**
     * 当前线程必须已绑定租户（如经网关/过滤器写入的 tenantId），否则抛出业务异常。
     * <p>用于文件存储等强租户隔离场景，禁止在缺少租户时使用 {@link #DEFAULT_TENANT_ID} 兜底。</p>
     */
    public static String requireTenantId() {
        String tenantId = TENANT_ID.get();
        if (StringUtils.isBlank(tenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "缺少租户标识 tenantId");
        }
        return tenantId.trim();
    }

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

