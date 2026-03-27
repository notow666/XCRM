package cn.cordys.common.context;

import cn.cordys.context.TenantContext;
import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
import cn.cordys.tenant.service.TenantMetaService;
import cn.cordys.common.util.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

public class TenantContextWebFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_HEADER = "X-Tenant-ID";
    public static final String TENANT_ID_QUERY = "tenantId";

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
        String tenantId = null;

        SessionUser sessionUser = SessionUtils.getUser();
        if (sessionUser != null) {
            // 管理中心登录走平台会话，不参与租户上下文校验
            if ("PLATFORM".equalsIgnoreCase(StringUtils.defaultString(sessionUser.getSource()))) {
                chain.doFilter(request, response);
                return;
            }
            tenantId = StringUtils.trimToNull(sessionUser.getTenantId());
            if (StringUtils.isBlank(tenantId)) {
                rejectIllegalTenant(response, "请求非法");
                return;
            }
            if (!tenantMetaService.isTenantEnabled(tenantId)) {
                SecurityUtils.getSubject().logout();
                rejectDisabledTenant(response, Translator.get("tenant.disabled"));
                return;
            }
            String headerTenant = request.getHeader(TENANT_ID_HEADER);
            if (StringUtils.isNotBlank(headerTenant) && !Objects.equals(headerTenant, tenantId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
                return;
            }
        } else {
            tenantId = request.getHeader(TENANT_ID_HEADER);
            if (StringUtils.isBlank(tenantId)) {
                tenantId = request.getParameter(TENANT_ID_QUERY);
            }
        }

        tenantId = StringUtils.trimToNull(tenantId);
        // tenantId 可能只在登录请求体中（Filter 不能读取 body），因此只有当 Filter 能拿到 tenantId 时才校验。
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
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

