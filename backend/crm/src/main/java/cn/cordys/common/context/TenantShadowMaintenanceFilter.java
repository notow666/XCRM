package cn.cordys.common.context;

import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.security.ShiroFilter;
import cn.cordys.tenant.service.TenantShadowMetaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A→B 切换维护窗：拦截 USER_REQUEST 并返回友好提示。
 */
public class TenantShadowMaintenanceFilter extends OncePerRequestFilter {

    private final TenantShadowMetaService tenantShadowMetaService;

    public TenantShadowMaintenanceFilter(TenantShadowMetaService tenantShadowMetaService) {
        this.tenantShadowMetaService = tenantShadowMetaService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (ShiroFilter.shouldNotFilter(request, true)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/sse/") || uri.contains("/platform/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.isNotBlank(tenantId)
                && RoutingContext.getPurposeOrDefault() == RoutingPurpose.USER_REQUEST
                && tenantShadowMetaService.isInMaintenanceBlocking(tenantId)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            ResultHolder result = ResultHolder.error(CrmHttpResultCode.FAILED.getCode(),
                    Translator.get("tenant.shadow.maintenance.blocking"));
            response.getWriter().write(JSON.toJSONString(result));
            return;
        }
        chain.doFilter(request, response);
    }
}
