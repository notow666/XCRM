package cn.cordys.common.context;

import cn.cordys.common.security.ApiKeyHandler;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
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
 * 按请求路径设置 {@link RoutingPurpose}，供 JDBC 双库路由使用。
 */
public class RoutingPurposeWebFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return ShiroFilter.shouldNotFilter(request, false);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        RoutingContext.setPurpose(resolvePurpose(request));
        try {
            chain.doFilter(request, response);
        } finally {
            RoutingContext.clear();
        }
    }

    static RoutingPurpose resolvePurpose(HttpServletRequest request) {
        String uri = StringUtils.defaultString(request.getRequestURI());
        if (uri.contains("/anonymous/mmba/callback")) {
            return RoutingPurpose.INTEGRATION_PRIMARY;
        }
        if (uri.contains("/anonymous/mmba/mgmt-sso/check")) {
            return RoutingPurpose.IDENTITY_PRIMARY;
        }
        if (uri.contains("/lead/push")) {
            return RoutingPurpose.INTEGRATION_PRIMARY;
        }
        if (uri.contains("/sso/callback/")) {
            return RoutingPurpose.IDENTITY_PRIMARY;
        }
        if (uri.contains("/user/sync/")) {
            return RoutingPurpose.IDENTITY_PRIMARY;
        }
        if (uri.contains("/data-specialist/pool/import")) {
            return RoutingPurpose.DATA_SPECIALIST_PRIMARY;
        }
        if (ApiKeyHandler.isApiKeyCall(request)) {
            return RoutingPurpose.EXTERNAL_API_PRIMARY;
        }
        return RoutingPurpose.USER_REQUEST;
    }
}
