package cn.cordys.config;

import cn.cordys.security.OnlineSessionRegistry;
import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUser;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Session 销毁时同步清理在线注册表。
 */
@Component
public class OnlineSessionRegistryListener {

    @Resource
    private OnlineSessionRegistry onlineSessionRegistry;

    @EventListener
    public void onSessionDeleted(SessionDeletedEvent event) {
        notifySessionEnded(event.getSessionId(), event.getSession());
    }

    @EventListener
    public void onSessionExpired(SessionExpiredEvent event) {
        notifySessionEnded(event.getSessionId(), event.getSession());
    }

    private void notifySessionEnded(String sessionId, org.springframework.session.Session session) {
        if (sessionId == null || onlineSessionRegistry == null) {
            return;
        }
        SessionUser sessionUser = null;
        if (session != null) {
            Object user = session.getAttribute(SessionConstants.ATTR_USER);
            if (user instanceof SessionUser su) {
                sessionUser = su;
            }
        }
        onlineSessionRegistry.onSessionEnded(sessionId, sessionUser);
    }
}
