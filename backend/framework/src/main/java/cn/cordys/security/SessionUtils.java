package cn.cordys.security;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Map;

import static cn.cordys.security.SessionConstants.ATTR_USER;


/**
 * Session 工具类，提供操作用户 Session 的常用方法。
 * <p>
 * 包含获取当前用户信息、获取 Session ID、踢除用户等功能。
 * </p>
 */
@Slf4j
public class SessionUtils {
    /**
     * 获取当前用户的 ID。
     *
     * @return 当前用户的 ID，如果没有获取到用户信息，则返回 null
     */
    public static String getUserId() {
        SessionUser user = getUser();
        return user == null ? null : user.getId();
    }

    /**
     * 获取当前用户信息。
     *
     * @return 当前用户对象，如果未获取到用户信息，则返回 null
     */
    public static SessionUser getUser() {
        try {
            // 仅信任当前线程 ThreadContext：避免 SecurityUtils.getSecurityManager() 回落到 JVM 静态单例
            // 导致“误判有 Manager、getSubject 却抛 No SecurityManager accessible”的日志与行为分裂
            if (ThreadContext.getSecurityManager() == null) {
                return null;
            }
            Subject subject = ThreadContext.getSubject();
            if (subject == null) {
                return null;
            }

            Session session = subject.getSession(false); // 不自动创建 session
            if (session == null) {
                return null;
            }

            Object user = session.getAttribute(ATTR_USER);
            return user instanceof SessionUser ? (SessionUser) user : null;
        } catch (Exception e) {
            log.warn("获取在线用户失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 先走 Shiro ThreadContext，再走 HttpSession（SSE 等 anon 链路仅有 Spring Session 时仍能读到用户）。
     */
    public static SessionUser getUser(HttpServletRequest request) {
        SessionUser fromShiro = getUser();
        if (fromShiro != null) {
            return fromShiro;
        }
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(ATTR_USER);
        return attr instanceof SessionUser ? (SessionUser) attr : null;
    }

    /**
     * 获取当前 Session 的 ID。
     *
     * @return 当前 Session 的 ID
     */
    public static String getSessionId() {
        try {
            if (ThreadContext.getSecurityManager() == null) {
                return null;
            }
            Subject subject = ThreadContext.getSubject();
            if (subject == null) {
                return null;
            }
            return (String) subject.getSession().getId();
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildPrincipalName(String source, String tenantId, String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        String normalizedSource = StringUtils.defaultIfBlank(source, LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name());
        String normalizedTenantId = StringUtils.trimToNull(tenantId);
        if (normalizedTenantId == null) {
            return null;
        }
        return normalizedSource + ":" + normalizedTenantId + ":" + userId;
    }

    public static String buildPrincipalName(SessionUser sessionUser) {
        if (sessionUser == null || StringUtils.isBlank(sessionUser.getId())) {
            return null;
        }
        return buildPrincipalName(sessionUser.getSource(), sessionUser.getTenantId(), sessionUser.getId());
    }

    /**
     * 踢除指定用户（按 source+tenantId+userId 维度删除会话）。
     *
     * @param source   用户来源
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    public static void kickOutUser(String source, String tenantId, String userId) {
        kickOutUserAndCount(source, tenantId, userId);
    }

    /**
     * 踢除指定用户并返回实际删除的 Session 数量。
     */
    public static int kickOutUserAndCount(String source, String tenantId, String userId) {
        if (StringUtils.isBlank(userId)) {
            return 0;
        }
        String principalName = buildPrincipalName(source, tenantId, userId);
        if (StringUtils.isBlank(principalName)) {
            log.warn("kickOutUser 跳过：tenantId 为空，避免按默认租户误踢 userId={}", userId);
            return 0;
        }

        OnlineSessionRegistry registry = CommonBeanFactory.getBean(OnlineSessionRegistry.class);
        if (registry != null) {
            return registry.kickPrincipal(principalName);
        }
        return kickOutUserByPrincipalIndex(principalName);
    }

    static int kickOutUserByPrincipalIndex(String principalName) {
        RedisIndexedSessionRepository sessionRepository = CommonBeanFactory.getBean(RedisIndexedSessionRepository.class);
        if (sessionRepository == null) {
            return 0;
        }
        Map<String, ?> users = sessionRepository.findByPrincipalName(principalName);
        if (MapUtils.isEmpty(users)) {
            return 0;
        }
        users.keySet().forEach(sessionRepository::deleteById);
        return users.size();
    }

    /**
     * 踢除指定用户（当前租户下 LOCAL 用户）。
     *
     * @param userId 用户ID
     */
    public static void kickOutUser(String userId) {
        String tenantId = StringUtils.trimToNull(TenantContext.getTenantId());
        if (tenantId == null) {
            log.warn("kickOutUser 跳过：当前线程未绑定租户 userId={}", userId);
            return;
        }
        kickOutUser(LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name(), tenantId, userId);
    }

    /**
     * 踢除指定用户（从 Redis 会话中删除）。
     *
     * @param operatorId 操作用户 ID
     * @param kickUserId 被踢用户 ID
     */
    public static void kickOutUser(String operatorId, String kickUserId) {
        SessionUser currentUser = getUser();
        // 处理用户会话
        boolean isSelfReset = Strings.CS.equals(operatorId, kickUserId);
        if (isSelfReset) {
            // 当前用户重置自己的密码，直接登出
            SecurityUtils.getSubject().logout();
            // 需要检查是否有其他会话存在
            try {
                if (currentUser != null) {
                    SessionUtils.kickOutUser(currentUser.getSource(), currentUser.getTenantId(), kickUserId);
                } else {
                    SessionUtils.kickOutUser(kickUserId);
                }
            } catch (Exception e) {
                log.error("踢出用户失败: {}", e.getMessage());
            }
        } else {
            // 管理员重置他人密码，踢出该用户
            if (currentUser != null) {
                SessionUtils.kickOutUser(currentUser.getSource(), currentUser.getTenantId(), kickUserId);
            } else {
                SessionUtils.kickOutUser(kickUserId);
            }
        }

    }

    /**
     * 将当前用户信息保存到 Session 中。
     *
     * @param sessionUser 当前用户对象
     */
    public static void putUser(SessionUser sessionUser) {
        Session session = SecurityUtils.getSubject().getSession();
        session.setAttribute(ATTR_USER, sessionUser);
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                buildPrincipalName(sessionUser)
        );
        notifyOnlineSessionRegistry(sessionUser, (String) session.getId());
    }

    private static void notifyOnlineSessionRegistry(SessionUser sessionUser, String sessionId) {
        if (sessionUser == null || StringUtils.isBlank(sessionId)) {
            return;
        }
        OnlineSessionRegistry registry = CommonBeanFactory.getBean(OnlineSessionRegistry.class);
        if (registry != null) {
            registry.onSessionUserBound(sessionUser, sessionId);
        }
    }

    /**
     * 主动登出：先销毁当前 Session，再清理在线注册表。
     */
    public static void logoutCurrentUser() {
        SessionUser sessionUser = getUser();
        String sessionId = getSessionId();
        SecurityUtils.getSubject().logout();
        OnlineSessionRegistry registry = CommonBeanFactory.getBean(OnlineSessionRegistry.class);
        if (registry != null && StringUtils.isNotBlank(sessionId)) {
            registry.onSessionEnded(sessionId, sessionUser);
        }
    }
}
