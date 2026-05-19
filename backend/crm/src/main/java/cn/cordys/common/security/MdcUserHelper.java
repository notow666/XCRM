package cn.cordys.common.security;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import static cn.cordys.common.constants.MdcConstants.USER_ID_KEY;
import static cn.cordys.common.constants.MdcConstants.USER_NAME_KEY;
import static cn.cordys.common.constants.MdcConstants.USER_SOURCE_KEY;

/**
 * 按登录用户类型统一写入 MDC，供链路日志与操作日志使用。
 */
public final class MdcUserHelper {

    private MdcUserHelper() {
    }

    public static void apply(HttpServletRequest request) {
        SessionUser user = resolveSessionUser(request);
        if (user != null) {
            if (StringUtils.isBlank(user.getId())) {
                return;
            }
            MDC.put(USER_ID_KEY, resolveMdcUserId(user));
            if (StringUtils.isNotBlank(user.getName())) {
                MDC.put(USER_NAME_KEY, user.getName());
            }
            if (StringUtils.isNotBlank(user.getSource())) {
                MDC.put(USER_SOURCE_KEY, user.getSource());
            }
        }
        else {
            String kind = StringUtils.trimToEmpty(request.getParameter("kind")).toUpperCase();
            if(SsePrincipalKind.PLATFORM.name().equals(kind)) {
                MDC.put(USER_ID_KEY, LoginAuthenticateConstants.PLATFORM_USER_PREFIX + request.getParameter(USER_ID_KEY));
                MDC.put(USER_SOURCE_KEY, LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name());
            }
            else if(SsePrincipalKind.DATA_SPECIALIST.name().equals(kind)) {
                MDC.put(USER_ID_KEY, DataSpecialistConstants.specialistUserId(request.getParameter(USER_ID_KEY)));
                MDC.put(USER_SOURCE_KEY, LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name());
            }
            else if(SsePrincipalKind.TENANT.name().equals(kind)) {
                MDC.put(USER_ID_KEY, request.getParameter(USER_ID_KEY));
                MDC.put(USER_SOURCE_KEY, LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name());
            }
        }
    }

    static String resolveMdcUserId(SessionUser user) {
        String source = StringUtils.defaultString(user.getSource());
        if (LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name().equalsIgnoreCase(source)) {
            return DataSpecialistConstants.specialistUserId(user.getId());
        }
        if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(source)) {
            return LoginAuthenticateConstants.PLATFORM_USER_PREFIX + user.getId();
        }
        return user.getId();
    }

    /**
     * 与 {@link SessionUtils#getUser()} 一致：先 Shiro，再 Spring Session 包装的 HttpSession。
     */
    static SessionUser resolveSessionUser(HttpServletRequest request) {
        SessionUser fromShiro = SessionUtils.getUser();
        if (fromShiro != null) {
            return fromShiro;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(SessionConstants.ATTR_USER);
        return attr instanceof SessionUser ? (SessionUser) attr : null;
    }
}
