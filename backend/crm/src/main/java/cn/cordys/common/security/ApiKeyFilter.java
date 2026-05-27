package cn.cordys.common.security;

import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.ShiroFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.filter.authc.AnonymousFilter;
import org.apache.shiro.web.util.WebUtils;

import java.io.IOException;

/**
 * 自定义过滤器，用于处理 Web 应用中的 API 密钥认证。
 * 继承 AnonymousFilter 支持 API 密钥认证和常规会话认证。
 */
public class ApiKeyFilter extends AnonymousFilter {

    private static final String NO_PASSWORD = "no_pass";

    @Override
    protected boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);

        if (ShiroFilter.shouldNotFilter(httpRequest, false)) {
            return true;
        }

        Boolean apiKeyCall = ApiKeyHandler.isApiKeyCall(httpRequest);
        if (!apiKeyCall && !SecurityUtils.getSubject().isAuthenticated()) {
            return true;
        }

        if (!SecurityUtils.getSubject().isAuthenticated()) {
            String userId = ApiKeyHandler.getUser(httpRequest);
            if (StringUtils.isNotBlank(userId)) {
                SecurityUtils.getSubject().login(new UsernamePasswordToken(userId, NO_PASSWORD));
            }
        }

        if (!SecurityUtils.getSubject().isAuthenticated()) {
            ((HttpServletResponse) response).setHeader(SessionConstants.AUTHENTICATION_STATUS, SessionConstants.AUTHENTICATION_INVALID);
        }

        return true;
    }

    @Override
    protected void postHandle(ServletRequest request, ServletResponse response) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);

        if (ApiKeyHandler.isApiKeyCall(httpRequest) && SecurityUtils.getSubject().isAuthenticated()) {
            SecurityUtils.getSubject().logout();
        }
    }

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        super.doFilterInternal(request, response, chain);
    }
}
