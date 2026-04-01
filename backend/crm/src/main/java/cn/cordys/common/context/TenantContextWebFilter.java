package cn.cordys.common.context;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.context.TenantContext;
import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.tenant.service.TenantMetaService;
import cn.cordys.common.util.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static cn.cordys.common.constants.MdcConstants.*;

public class TenantContextWebFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";

    private final TenantMetaService tenantMetaService;

    public TenantContextWebFilter(TenantMetaService tenantMetaService) {
        this.tenantMetaService = tenantMetaService;
    }

    private void rejectIllegalTenant(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        ResultHolder result = ResultHolder.error(CrmHttpResultCode.VALIDATE_FAILED.getCode(), message);
        response.getWriter().write(JSON.toJSONString(result));
    }

    private void rejectDisabledTenant(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        ResultHolder result = ResultHolder.error(CrmHttpResultCode.FORBIDDEN.getCode(), message);
        response.getWriter().write(JSON.toJSONString(result));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/platform/")
                || uri.contains("/system/version"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (StringUtils.isBlank(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_KEY);
        }

        tenantId = StringUtils.trimToNull(tenantId);
        if (StringUtils.isNotBlank(tenantId)) {
            if (!tenantMetaService.existsTenantId(tenantId)) {
                rejectIllegalTenant(response, "请求非法");
                return;
            }
            if (!tenantMetaService.isTenantEnabled(tenantId)) {
                rejectDisabledTenant(response, Translator.get("tenant.disabled"));
                return;
            }
            TenantContext.setTenantId(tenantId);

            // MDC
            String traceId = IDGenerator.nextStr();
            MDC.put(TRACE_ID_KEY, traceId);

            MDC.put(TENANT_ID_KEY, tenantId);

            String clientIp = getClientIp(request);
            MDC.put(CLIENT_IP_KEY, clientIp);

            MDC.put(REQUEST_URI_KEY, request.getRequestURI());
            MDC.put(REQUEST_METHOD_KEY, request.getMethod());

            SessionUser sessionUser = SessionUtils.getUser();
            if(sessionUser != null) {
                MDC.put(USER_ID_KEY, sessionUser.getId());
                MDC.put(USER_NAME_KEY, sessionUser.getName());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

