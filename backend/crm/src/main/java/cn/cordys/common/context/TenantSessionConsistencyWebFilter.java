package cn.cordys.common.context;

import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.ShiroFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 已登录时以会话租户覆盖 {@link cn.cordys.context.TenantContext}（不信任客户端 Header/Query）；
 * 数据专员则校验当前 Context 租户在其授权范围内。
 */
public class TenantSessionConsistencyWebFilter extends OncePerRequestFilter {

    private final ExtDataSpecialistMapper extDataSpecialistMapper;

    public TenantSessionConsistencyWebFilter(ExtDataSpecialistMapper extDataSpecialistMapper) {
        this.extDataSpecialistMapper = extDataSpecialistMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        return ShiroFilter.shouldNotFilter(request, true);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        SessionUser user = SessionUtils.getUser(request);
        if (user != null) {
            TenantSessionBindingSupport.alignTenantContextFromSession(user, extDataSpecialistMapper);
        }
        chain.doFilter(request, response);
    }
}
