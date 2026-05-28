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

    public static void assertTenantSseSubscription(SessionUser user, String tenantId, String userId, ExtDataSpecialistMapper specialistMapper) {
        if (user == null) {
            throw new cn.cordys.common.exception.GenericException(cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN, "未登录");
        }
        if (!Strings.CI.equals(StringUtils.trimToEmpty(user.getId()), StringUtils.trimToEmpty(userId))) {
            throw new cn.cordys.common.exception.GenericException(cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN, "无权订阅该用户消息");
        }
        String tid = StringUtils.trimToNull(tenantId);
        if (tid == null) {
            tid = StringUtils.trimToNull(TenantContext.getTenantId());
        }
        if (isDataSpecialistUser(user)) {
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
        if (sessionTenant == null || tid == null || !Strings.CI.equals(sessionTenant, tid)) {
            throw new cn.cordys.common.exception.GenericException(
                    cn.cordys.common.response.result.CrmHttpResultCode.FORBIDDEN,
                    "租户与会话不一致");
        }
    }
}
