package cn.cordys.common.context;

import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.JSON;
import cn.cordys.platform.service.PlatformSystemMaintenanceService;
import cn.cordys.security.ShiroFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 维护排水模式：拦截租户侧与数据专员侧新请求，平台管理接口仍可访问。
 */
public class SystemMaintenanceGateFilter extends OncePerRequestFilter {

    private static final String MAINTENANCE_MESSAGE = "系统维护中，请稍后再试";

    private final PlatformSystemMaintenanceService platformSystemMaintenanceService;

    public SystemMaintenanceGateFilter(PlatformSystemMaintenanceService platformSystemMaintenanceService) {
        this.platformSystemMaintenanceService = platformSystemMaintenanceService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        return ShiroFilter.shouldNotFilter(request, true);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!platformSystemMaintenanceService.isMaintenanceMode()) {
            chain.doFilter(request, response);
            return;
        }

        String uri = normalizeUri(request.getRequestURI());
        if (isWhitelisted(uri)) {
            chain.doFilter(request, response);
            return;
        }

        rejectMaintenance(response);
    }

    private String normalizeUri(String uri) {
        if (uri == null) {
            return "";
        }
        int semicolon = uri.indexOf(';');
        if (semicolon > 0) {
            uri = uri.substring(0, semicolon);
        }
        return uri;
    }

    private boolean isWhitelisted(String uri) {
        if (StringUtils.isBlank(uri)) {
            return false;
        }
        if (uri.startsWith("/platform/")) {
            return true;
        }
        if ("/system/version".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/sse/subscribe") || uri.startsWith("/sse/close")) {
            return true;
        }
        return false;
    }

    private void rejectMaintenance(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        ResultHolder result = ResultHolder.error(CrmHttpResultCode.FAILED.getCode(), MAINTENANCE_MESSAGE);
        response.getWriter().write(JSON.toJSONString(result));
    }
}
