package cn.cordys.common.context;

import cn.cordys.common.constants.CommonConstants;
import cn.cordys.context.TenantContext;
import cn.cordys.common.response.handler.ResultHolder;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
import cn.cordys.security.CommonUtils;
import cn.cordys.security.ShiroFilter;
import cn.cordys.tenant.service.TenantMetaService;
import cn.cordys.common.util.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
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
        return ShiroFilter.shouldNotFilter(request, true);
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
        boolean b = CommonUtils.requiresTenantForSse(uri);
        boolean b1 = CommonUtils.sseExemptFromTenant(request);

        boolean needTenantForSse = b && !b1;
        if ((CommonUtils.requiresTenantForAnonymousFilePreview(uri) || needTenantForSse) && StringUtils.isBlank(tenantId)) {
            rejectIllegalTenant(response, "缺少租户标识 tenantId");
            return;
        }

        // 平台 / 数据专员 SSE 不绑定租户；若请求仍带占位头（如 ---），不得按真实租户校验否则 400「请求非法」
        boolean sseSkipTenantResolution = b && b1;
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
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

