package cn.cordys.context;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "default";
    /**
     * 使用普通 {@link ThreadLocal}，避免 {@link InheritableThreadLocal} 在子线程创建时复制错误租户上下文。
     */
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 未绑定或仅空白时返回 {@code null}，不向 {@link #DEFAULT_TENANT_ID} 兜底，避免误路由到其他租户数据。
     * <p>需要显式使用默认租户时，调用方自行 {@code StringUtils.defaultIfBlank(..., DEFAULT_TENANT_ID)}。</p>
     */
    public static String getTenantIdOrDefault() {
        return StringUtils.trimToNull(TENANT_ID.get());
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
        tenantId = StringUtils.trimToNull(tenantId);
        if(StringUtils.isBlank(tenantId)) {
            log.warn("线程设置tenantId为空");
            return;
        }
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

