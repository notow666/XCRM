package cn.cordys.common.context;

import cn.cordys.common.constants.CommonConstants;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.context.TenantContext;
import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
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
                || uri.contains("/system/version") || uri.contains("/anonymous/mmba/callback")
                || uri.contains("/anonymous/mmba/mgmt-sso/check"));
    }

    /**
     * 图片/附件预览为 Shiro 匿名接口，浏览器直连不会带 X-Tenant-ID，须通过查询参数 tenantId 与过滤器一致写入 {@link TenantContext}。
     */
    private static boolean requiresTenantForAnonymousFilePreview(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.contains("/pic/preview/") || uri.contains("/attachment/preview/");
    }

    /**
     * 浏览器 {@code EventSource} 无法自定义请求头，须通过查询参数 {@code tenantId} 写入 {@link TenantContext}（与请求头二选一）。
     */
    private static boolean requiresTenantForSse(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.contains("/sse/subscribe") || uri.contains("/sse/close") || uri.contains("/sse/broadcast");
    }

    /**
     * 平台管理员、数据专员 SSE 不绑定租户上下文（与 {@code kind} 查询参数一致）。
     */
    private static boolean sseExemptFromTenant(HttpServletRequest request) {
        String kind = StringUtils.trimToEmpty(request.getParameter("kind")).toUpperCase();
        return "PLATFORM".equals(kind) || "DATA_SPECIALIST".equals(kind);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String tenantId = request.getHeader(CommonConstants.TENANT_ID_HEADER);
        if (StringUtils.isBlank(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_KEY);
        }

        tenantId = StringUtils.trimToNull(tenantId);
        boolean needTenantForSse = requiresTenantForSse(uri) && !sseExemptFromTenant(request);
        // 平台 / 数据专员 SSE 不绑定租户；若请求仍带占位头（如 ---），不得按真实租户校验否则 400「请求非法」
        boolean sseSkipTenantResolution = requiresTenantForSse(uri) && sseExemptFromTenant(request);
        if ((requiresTenantForAnonymousFilePreview(uri) || needTenantForSse) && tenantId == null) {
            rejectIllegalTenant(response, "缺少租户标识 tenantId");
            return;
        }
        if (StringUtils.isNotBlank(tenantId) && !sseSkipTenantResolution) {
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

            if(needTenantForSse) {
                MDC.put(USER_ID_KEY, request.getParameter(USER_ID_KEY));
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

