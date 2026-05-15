package cn.cordys.security;

import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

public class CommonUtils {
    /**
     * 图片/附件预览为 Shiro 匿名接口，浏览器直连不会带 X-Tenant-ID，须通过查询参数 tenantId 与过滤器一致写入 {@link TenantContext}。
     */
    public static boolean requiresTenantForAnonymousFilePreview(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.contains("/pic/preview/") || uri.contains("/attachment/preview/");
    }

    /**
     * 浏览器 {@code EventSource} 无法自定义请求头，须通过查询参数 {@code tenantId} 写入 {@link TenantContext}（与请求头二选一）。
     */
    public static boolean requiresTenantForSse(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.contains("/sse/subscribe") || uri.contains("/sse/close") || uri.contains("/sse/broadcast");
    }

    /**
     * 平台管理员、数据专员 SSE 不绑定租户上下文（与 {@code kind} 查询参数一致）。
     */
    public static boolean sseExemptFromTenant(HttpServletRequest request) {
        String kind = StringUtils.trimToEmpty(request.getParameter("kind")).toUpperCase();
        return SsePrincipalKind.PLATFORM.name().equals(kind) || SsePrincipalKind.DATA_SPECIALIST.name().equals(kind);
    }

    public static boolean sseExemptFromTenant(String kind) {
        return SsePrincipalKind.PLATFORM.name().equals(kind) || SsePrincipalKind.DATA_SPECIALIST.name().equals(kind);
    }
}
