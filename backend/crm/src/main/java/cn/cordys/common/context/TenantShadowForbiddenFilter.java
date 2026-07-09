package cn.cordys.common.context;

import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.support.MmbaOutboundPaths;
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
 * 影子库 active 时禁止 MMBA 出站类接口。
 */
public class TenantShadowForbiddenFilter extends OncePerRequestFilter {

    private final TenantShadowMetaService tenantShadowMetaService;

    public TenantShadowForbiddenFilter(TenantShadowMetaService tenantShadowMetaService) {
        this.tenantShadowMetaService = tenantShadowMetaService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return ShiroFilter.shouldNotFilter(request, true);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.isNotBlank(tenantId)
                && tenantShadowMetaService.isShadowActive(tenantId)
                && RoutingContext.getPurposeOrDefault() == RoutingPurpose.USER_REQUEST
                && MmbaOutboundPaths.isOutboundRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            ResultHolder result = ResultHolder.error(CrmHttpResultCode.FORBIDDEN.getCode(),
                    Translator.get("tenant.shadow.mmba.outbound.forbidden"));
            response.getWriter().write(JSON.toJSONString(result));
            return;
        }
        chain.doFilter(request, response);
    }
}
