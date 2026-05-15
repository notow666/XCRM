package cn.cordys.common.context;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.common.security.MdcUserHelper;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.ServletUtils;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static cn.cordys.common.constants.MdcConstants.*;
import static cn.cordys.common.constants.MdcConstants.REQUEST_METHOD_KEY;

/**
 * 将当前登录用户写入 MDC。
 * <p>
 * 优先使用 {@link SessionUtils#getUser()}（依赖 Shiro 已绑定 ThreadContext）；
 * 若为空则从 {@link HttpSession} 读取 {@link SessionConstants#ATTR_USER}，避免过滤器链中
 * Shiro 尚未绑定 Subject 时 MDC 长期为空。
 * </p>
 */
public class MdcUserContextFilter extends OncePerRequestFilter {

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

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // MDC
        String traceId = IDGenerator.nextStr();
        MDC.put(TRACE_ID_KEY, traceId);

        SessionUser user = resolveSessionUser(request);
        boolean applied = false;
        if (user != null) {
            MdcUserHelper.apply(user);

            String clientIp = ServletUtils.getClientIp(request);
            MDC.put(CLIENT_IP_KEY, clientIp);

            MDC.put(REQUEST_URI_KEY, request.getRequestURI());
            MDC.put(REQUEST_METHOD_KEY, request.getMethod());

            applied = true;
        }
        else {
            String kind = StringUtils.trimToEmpty(request.getParameter("kind")).toUpperCase();
            if(SsePrincipalKind.PLATFORM.name().equals(kind)) {
                MDC.put(USER_ID_KEY, LoginAuthenticateConstants.PLATFORM_USER_PREFIX + request.getParameter(USER_ID_KEY));
            }
            else if(SsePrincipalKind.DATA_SPECIALIST.name().equals(kind)) {
                MDC.put(USER_ID_KEY, DataSpecialistConstants.specialistUserId(request.getParameter(USER_ID_KEY)));
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (applied) {
                MdcUserHelper.clear();
            }
        }
    }
}
