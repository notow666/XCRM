package cn.cordys.common.context;

import cn.cordys.common.security.MdcUserHelper;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.ServletUtils;
import cn.cordys.context.TenantContext;
import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.ShiroFilter;
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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        return ShiroFilter.shouldNotFilter(request, false);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // MDC
        String traceId = IDGenerator.nextStr();
        MDC.put(TRACE_ID_KEY, traceId);

        String clientIp = ServletUtils.getClientIp(request);
        MDC.put(CLIENT_IP_KEY, clientIp);
        MDC.put(REQUEST_URI_KEY, request.getRequestURI());
        MDC.put(REQUEST_METHOD_KEY, request.getMethod());

        MdcUserHelper.apply(request);

        String tenantId = TenantContext.getTenantId();
        if(StringUtils.isNotBlank(tenantId)) {
            MDC.put(TENANT_ID_KEY, tenantId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
