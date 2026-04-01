package cn.cordys.common.context;

import cn.cordys.context.OrganizationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 组织信息的 Web 过滤器
 * 拦截请求，设置组织上下文
 *
 * @author jianxing
 */
public class OrganizationContextWebFilter extends OncePerRequestFilter {
    /**
     * 组织信息的请求头名称
     */
    public static final String ORGANIZATION_ID_HEADER = "Organization-Id";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/platform/")
                || uri.contains("/system/version"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String organizationId = request.getHeader(ORGANIZATION_ID_HEADER);
        if (organizationId != null) {
            OrganizationContext.setOrganizationId(organizationId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 清理
            OrganizationContext.clear();
        }
    }

}
