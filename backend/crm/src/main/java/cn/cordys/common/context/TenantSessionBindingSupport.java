package cn.cordys.common.context;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.context.TenantContext;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * 已登录请求的租户绑定：普通租户以会话为准；数据专员校验授权租户列表。
 */
public final class TenantSessionBindingSupport {

    private static final String PLACEHOLDER_TENANT = "---";
    /** 与前端路由占位 {@code default} 一致：URL 未显式指定租户时，SSE 等应回落到会话租户 */
    private static final String RESERVED_DEFAULT_TENANT_ID = "default";

    private TenantSessionBindingSupport() {
    }

    public static boolean isPlatformUser(SessionUser user) {
        return Strings.CI.equals(
                StringUtils.trimToEmpty(user.getSource()),
                LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name());
    }

    public static boolean isDataSpecialistUser(SessionUser user) {
        return Strings.CI.equals(
                StringUtils.trimToEmpty(user.getSource()),
                LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name());
    }

    /** 租户 CRM 用户（须绑定单一 session.tenantId） */
    public static boolean isTenantCrmUser(SessionUser user) {
        return user != null && !isPlatformUser(user) && !isDataSpecialistUser(user);
    }

    /**
     * 将 {@link TenantContext} 与当前会话对齐。
     *
     * @return true 表示已按会话覆盖或专员校验通过；false 表示无需处理（未登录等）
     */
    public static boolean alignTenantContextFromSession(SessionUser user, ExtDataSpecialistMapper specialistMapper) {
        if (user == null) {
            return false;
        }
        if (isPlatformUser(user)) {
            return false;
        }
        if (isDataSpecialistUser(user)) {
            if (specialistMapper != null) {
                assertDataSpecialistTenantAllowed(user, specialistMapper);
            }
            return true;
        }
        String sessionTenantId = StringUtils.trimToNull(user.getTenantId());
        if (sessionTenantId == null) {
            return false;
        }
        TenantContext.setTenantId(sessionTenantId);
        return true;
    }

    public static void assertDataSpecialistTenantAllowed(SessionUser specialist, ExtDataSpecialistMapper specialistMapper) {
        String contextTenantId = StringUtils.trimToNull(TenantContext.getTenantId());
        if (contextTenantId == null || PLACEHOLDER_TENANT.equals(contextTenantId)) {
            return;
        }
        if (specialistMapper.existsTenantBinding(specialist.getId(), contextTenantId) <= 0) {
            throw new cn.cordys.common.exception.GenericException(
                    cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN,
                    "无权访问该租户");
        }
    }

    /**
     * SSE 订阅校验；失败时返回 false（避免对 EventSource 抛 JSON 异常导致 406）。
     */
    public static boolean validateTenantSseSubscription(
            SessionUser user, String tenantId, String userId, ExtDataSpecialistMapper specialistMapper) {
        try {
            assertTenantSseSubscription(user, tenantId, userId, specialistMapper);
            return true;
        } catch (cn.cordys.common.exception.GenericException ex) {
            return false;
        }
    }

    /**
     * SSE 订阅使用的有效租户 ID。
     * 租户 CRM 用户以会话租户为准（与 {@link #alignTenantContextFromSession} 一致），忽略 query 中可能过期的 tenantId。
     */
    public static String resolveSseTenantId(SessionUser user, String tenantId) {
        if (user != null && isTenantCrmUser(user)) {
            return StringUtils.trimToNull(user.getTenantId());
        }
        String tid = StringUtils.trimToNull(tenantId);
        if (tid == null) {
            tid = StringUtils.trimToNull(TenantContext.getTenantId());
        }
        if (user != null && isDataSpecialistUser(user) && tid != null
                && RESERVED_DEFAULT_TENANT_ID.equalsIgnoreCase(tid)) {
            tid = null;
        }
        return tid;
    }

    public static void assertTenantSseSubscription(SessionUser user, String tenantId, String userId, ExtDataSpecialistMapper specialistMapper) {
        if (user == null) {
            throw new cn.cordys.common.exception.GenericException(cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN, "未登录");
        }
        if (!Strings.CI.equals(StringUtils.trimToEmpty(user.getId()), StringUtils.trimToEmpty(userId))) {
            throw new cn.cordys.common.exception.GenericException(cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN, "无权订阅该用户消息");
        }
        if (isDataSpecialistUser(user)) {
            String tid = resolveSseTenantId(user, tenantId);
            if (tid != null && !PLACEHOLDER_TENANT.equals(tid)) {
                if (specialistMapper.existsTenantBinding(user.getId(), tid) <= 0) {
                    throw new cn.cordys.common.exception.GenericException(
                            cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN,
                            "无权访问该租户");
                }
            }
            return;
        }
        if (isPlatformUser(user)) {
            return;
        }
        String sessionTenant = StringUtils.trimToNull(user.getTenantId());
        if (sessionTenant == null) {
            throw new cn.cordys.common.exception.GenericException(
                    cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN,
                    "缺少会话租户");
        }
    }
}
