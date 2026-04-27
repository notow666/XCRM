package cn.cordys.common.security;

import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
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
import org.slf4j.MDC;

import java.io.IOException;

import static cn.cordys.common.constants.MdcConstants.USER_ID_KEY;
import static cn.cordys.common.constants.MdcConstants.USER_NAME_KEY;

/**
 * 自定义过滤器，用于处理 Web 应用中的 API 密钥认证。
 * 继承 AnonymousFilter 支持 API 密钥认证和常规会话认证。
 */
public class ApiKeyFilter extends AnonymousFilter {

    private static final String NO_PASSWORD = "no_pass"; // 默认的密码，用于 API 密钥认证

    /**
     * 在处理请求之前调用。该方法检查请求是否使用 API 密钥。
     * 如果没有 API 密钥且用户未认证，则允许请求通过。
     * 如果提供了 API 密钥，则使用该密钥尝试认证用户。
     *
     * @param request     Servlet 请求
     * @param response    Servlet 响应
     * @param mappedValue 过滤器链中的映射值
     *
     * @return 如果请求应该继续处理返回 true，否则返回 false
     */
    @Override
    protected boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);

        String uri = httpRequest.getRequestURI();
        if(uri != null && uri.contains("/anonymous/mmba/callback")){
            return true;
        }

        // 如果不是 API 密钥请求且用户未认证，允许请求继续
        Boolean apiKeyCall = ApiKeyHandler.isApiKeyCall(httpRequest);
        if (!apiKeyCall && !SecurityUtils.getSubject().isAuthenticated()) {
            return true;
        }

        // 处理 API 密钥认证
        if (!SecurityUtils.getSubject().isAuthenticated()) {
            String userId = ApiKeyHandler.getUser(httpRequest);
            if (StringUtils.isNotBlank(userId)) {
                // 使用 API 密钥中的用户 ID 进行认证，密码设置为默认值
                SecurityUtils.getSubject().login(new UsernamePasswordToken(userId, NO_PASSWORD));
            }
        }

        // 如果仍未认证，设置响应头为无效状态
        if (!SecurityUtils.getSubject().isAuthenticated()) {
            ((HttpServletResponse) response).setHeader(SessionConstants.AUTHENTICATION_STATUS, "invalid");
        }
        else{
            SessionUser sessionUser = SessionUtils.getUser();
            if(sessionUser != null) {
                MDC.put(USER_ID_KEY, sessionUser.getId());
                MDC.put(USER_NAME_KEY, sessionUser.getName());
            }
        }

        return true;
    }

    /**
     * 在请求处理之后调用。此方法用于处理 API 密钥退出逻辑。
     * 如果是 API 密钥请求并且用户已认证，则注销当前用户。
     *
     * @param request  Servlet 请求
     * @param response Servlet 响应
     */
    @Override
    protected void postHandle(ServletRequest request, ServletResponse response) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);

        // 如果是 API 密钥请求且用户已认证，则注销用户
        if (ApiKeyHandler.isApiKeyCall(httpRequest) && SecurityUtils.getSubject().isAuthenticated()) {
            SecurityUtils.getSubject().logout();
        }
    }

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        super.doFilterInternal(request, response, chain);
    }
}
