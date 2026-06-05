package cn.cordys.config;

import cn.cordys.security.SessionConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.Collections;
import java.util.List;

/**
 * Spring Session ID 解析：默认读 {@link SessionConstants#HEADER_TOKEN}；
 * SSE {@code EventSource} 无法自定义 Header，允许通过 query {@value #SSE_SESSION_QUERY_PARAM} 传递同一 sessionId。
 */
public class HeaderOrSseQuerySessionIdResolver implements HttpSessionIdResolver {

    /** 与前端 localStorage sessionId / X-AUTH-TOKEN 值一致 */
    public static final String SSE_SESSION_QUERY_PARAM = "sessionId";

    private final HeaderHttpSessionIdResolver headerResolver =
            new HeaderHttpSessionIdResolver(SessionConstants.HEADER_TOKEN);

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        List<String> fromHeader = headerResolver.resolveSessionIds(request);
        if (!fromHeader.isEmpty()) {
            return fromHeader;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/sse/")) {
            String sessionId = StringUtils.trimToNull(request.getParameter(SSE_SESSION_QUERY_PARAM));
            if (sessionId != null) {
                return Collections.singletonList(sessionId);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        headerResolver.setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        headerResolver.expireSession(request, response);
    }
}
