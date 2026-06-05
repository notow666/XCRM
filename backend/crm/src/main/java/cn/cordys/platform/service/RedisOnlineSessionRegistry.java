package cn.cordys.platform.service;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.security.OnlineSessionRegistry;
import cn.cordys.security.OnlineSessionStats;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Redis 在线 Session 注册表：登录绑定、登出清理、按 principal 踢人、平台在线统计。
 */
@Slf4j
@Service
public class RedisOnlineSessionRegistry implements OnlineSessionRegistry {

    private static final String KEY_PREFIX = "crm:online:";
    private static final String KEY_PRINCIPAL_SESSIONS = KEY_PREFIX + "principal:sessions:";
    private static final String KEY_SESSION_PRINCIPAL = KEY_PREFIX + "session:principal:";
    private static final String KEY_LOCAL_PRINCIPALS = KEY_PREFIX + "local:principals";
    private static final String KEY_DS_PRINCIPALS = KEY_PREFIX + "ds:principals";
    private static final String KEY_PLATFORM_PRINCIPALS = KEY_PREFIX + "platform:principals";
    private static final String KEY_LOCAL_TENANT = KEY_PREFIX + "local:tenant:";
    private static final String PLACEHOLDER_TENANT = "---";
    private static final Duration REGISTRY_TTL = Duration.ofSeconds(43200);
    private static final String PRINCIPAL_INDEX_PREFIX =
            "spring:session:index:" + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    @Override
    public void onSessionUserBound(SessionUser sessionUser, String sessionId) {
        String principal = SessionUtils.buildPrincipalName(sessionUser);
        if (StringUtils.isBlank(principal) || StringUtils.isBlank(sessionId)) {
            return;
        }
        String previousPrincipal = stringRedisTemplate.opsForValue().get(KEY_SESSION_PRINCIPAL + sessionId);
        if (StringUtils.isNotBlank(previousPrincipal) && !StringUtils.equals(previousPrincipal, principal)) {
            unregisterSession(sessionId, previousPrincipal);
        }

        stringRedisTemplate.opsForValue().set(KEY_SESSION_PRINCIPAL + sessionId, principal, REGISTRY_TTL);
        stringRedisTemplate.opsForSet().add(KEY_PRINCIPAL_SESSIONS + principal, sessionId);
        touchTtl(KEY_PRINCIPAL_SESSIONS + principal);

        String source = normalizedSource(sessionUser.getSource());
        if (LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name().equalsIgnoreCase(source)) {
            stringRedisTemplate.opsForSet().add(KEY_LOCAL_PRINCIPALS, principal);
            touchTtl(KEY_LOCAL_PRINCIPALS);
            String tenantId = StringUtils.trimToNull(sessionUser.getTenantId());
            if (tenantId != null && !PLACEHOLDER_TENANT.equals(tenantId)) {
                stringRedisTemplate.opsForSet().add(KEY_LOCAL_TENANT + tenantId, principal);
                touchTtl(KEY_LOCAL_TENANT + tenantId);
            }
        } else if (LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name().equalsIgnoreCase(source)) {
            stringRedisTemplate.opsForSet().add(KEY_DS_PRINCIPALS, principal);
            touchTtl(KEY_DS_PRINCIPALS);
        } else if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(source)) {
            stringRedisTemplate.opsForSet().add(KEY_PLATFORM_PRINCIPALS, principal);
            touchTtl(KEY_PLATFORM_PRINCIPALS);
        }
    }

    @Override
    public void onSessionEnded(String sessionId, SessionUser sessionUser) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        String principal = stringRedisTemplate.opsForValue().get(KEY_SESSION_PRINCIPAL + sessionId);
        if (StringUtils.isBlank(principal) && sessionUser != null) {
            principal = SessionUtils.buildPrincipalName(sessionUser);
        }
        stringRedisTemplate.delete(KEY_SESSION_PRINCIPAL + sessionId);
        if (StringUtils.isNotBlank(principal)) {
            unregisterSession(sessionId, principal);
        }
    }

    @Override
    public int kickPrincipal(String principal) {
        if (StringUtils.isBlank(principal)) {
            return 0;
        }
        Set<String> sessionIds = collectSessionIdsForKick(principal);
        if (sessionIds.isEmpty()) {
            purgePrincipalRegistry(principal);
            return 0;
        }
        int deleted = 0;
        for (String sessionId : sessionIds) {
            try {
                redisIndexedSessionRepository.deleteById(sessionId);
                deleted++;
            } catch (Exception e) {
                log.warn("删除 Session 失败 sessionId={}: {}", sessionId, e.getMessage());
            }
        }
        purgePrincipalRegistry(principal);
        return deleted;
    }

    @Override
    public OnlineSessionStats snapshot() {
        OnlineSessionStats stats = new OnlineSessionStats();
        Set<String> allPrincipals = new HashSet<>();

        Set<String> localPrincipals = members(KEY_LOCAL_PRINCIPALS);
        Set<String> dsPrincipals = members(KEY_DS_PRINCIPALS);
        Set<String> platformPrincipals = members(KEY_PLATFORM_PRINCIPALS);

        Set<String> activeLocal = activePrincipals(localPrincipals);
        Set<String> activeDs = activePrincipals(dsPrincipals);
        Set<String> activePlatform = activePrincipals(platformPrincipals);

        allPrincipals.addAll(activeLocal);
        allPrincipals.addAll(activeDs);
        allPrincipals.addAll(activePlatform);

        Map<String, Integer> sessionCountByPrincipal = new HashMap<>();
        Map<String, Set<String>> localPrincipalsByTenant = new HashMap<>();

        for (String principal : activeLocal) {
            int count = sessionCountForPrincipal(principal);
            sessionCountByPrincipal.put(principal, count);
            String tenantId = parseTenantId(principal);
            if (tenantId != null) {
                localPrincipalsByTenant.computeIfAbsent(tenantId, k -> new HashSet<>()).add(principal);
            }
        }
        for (String principal : activeDs) {
            sessionCountByPrincipal.put(principal, sessionCountForPrincipal(principal));
        }
        for (String principal : activePlatform) {
            sessionCountByPrincipal.put(principal, sessionCountForPrincipal(principal));
        }

        Set<String> tenantAndDs = new HashSet<>(activeLocal);
        tenantAndDs.addAll(activeDs);

        stats.setOnlineUserTotal(allPrincipals.size());
        stats.setOnlineTenantUserTotal(activeLocal.size());
        stats.setOnlinePlatformUserCount(activePlatform.size());
        stats.setOnlineDataSpecialistUserCount(activeDs.size());
        stats.setLocalPrincipalsByTenant(localPrincipalsByTenant);
        stats.setSessionCountByPrincipal(sessionCountByPrincipal);
        stats.setTenantAndDataSpecialistPrincipals(tenantAndDs);
        stats.setOnlineMultiDeviceUserCount(
                sessionCountByPrincipal.values().stream().filter(c -> c != null && c > 1).count());
        return stats;
    }

    @Override
    public void reconcile(OnlineSessionStats scanned) {
        if (scanned == null) {
            return;
        }
        Set<String> scannedPrincipals = new HashSet<>();
        if (scanned.getLocalPrincipalsByTenant() != null) {
            scanned.getLocalPrincipalsByTenant().values().forEach(scannedPrincipals::addAll);
        }
        if (scanned.getSessionCountByPrincipal() != null) {
            scanned.getSessionCountByPrincipal().keySet().stream()
                    .filter(p -> p.startsWith(LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name() + ":"))
                    .forEach(scannedPrincipals::add);
            scannedPrincipals.addAll(scanned.getSessionCountByPrincipal().keySet());
        }

        Set<String> registryLocals = members(KEY_LOCAL_PRINCIPALS);
        Set<String> registryDs = members(KEY_DS_PRINCIPALS);
        Set<String> registryAll = new HashSet<>();
        registryAll.addAll(registryLocals);
        registryAll.addAll(registryDs);
        registryAll.addAll(members(KEY_PLATFORM_PRINCIPALS));

        Set<String> scannedAll = scanned.getSessionCountByPrincipal() == null
                ? scannedPrincipals
                : scanned.getSessionCountByPrincipal().keySet();

        for (String stale : difference(registryAll, scannedAll)) {
            purgePrincipalRegistry(stale);
        }
        warmUpFromExistingSessions();
    }

    @Override
    public void warmUpFromExistingSessions() {
        int bound = 0;
        ScanOptions options = ScanOptions.scanOptions().match(PRINCIPAL_INDEX_PREFIX + "*").count(1000).build();
        try (Cursor<String> scan = stringRedisTemplate.scan(options)) {
            while (scan.hasNext()) {
                String indexKey = scan.next();
                String principal = indexKey.substring(PRINCIPAL_INDEX_PREFIX.length());
                if (StringUtils.isBlank(principal)) {
                    continue;
                }
                Set<String> sessionIds = members(indexKey);
                if (sessionIds.isEmpty()) {
                    Map<String, ?> fromRepo = redisIndexedSessionRepository.findByPrincipalName(principal);
                    if (fromRepo != null) {
                        sessionIds = fromRepo.keySet();
                    }
                }
                for (String sessionId : sessionIds) {
                    if (StringUtils.isBlank(sessionId)) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_SESSION_PRINCIPAL + sessionId))) {
                        continue;
                    }
                    SessionUser sessionUser = loadSessionUser(sessionId);
                    if (sessionUser == null) {
                        continue;
                    }
                    onSessionUserBound(sessionUser, sessionId);
                    bound++;
                }
            }
        } catch (Exception e) {
            log.warn("在线 Session 注册表补写失败: {}", e.getMessage());
        }
        if (bound > 0) {
            log.info("在线 Session 注册表补写 {} 条 Session", bound);
        }
    }

    private SessionUser loadSessionUser(String sessionId) {
        String sessionKey = "spring:session:sessions:" + sessionId;
        Object userObj = redisIndexedSessionRepository.getSessionRedisOperations()
                .opsForHash().get(sessionKey, "sessionAttr:user");
        return userObj instanceof SessionUser su ? su : null;
    }

    private Set<String> activePrincipals(Set<String> principals) {
        Set<String> active = new HashSet<>();
        for (String principal : principals) {
            if (sessionCountForPrincipal(principal) > 0) {
                active.add(principal);
            }
        }
        return active;
    }

    private Set<String> collectSessionIdsForKick(String principal) {
        Set<String> sessionIds = new LinkedHashSet<>();
        sessionIds.addAll(members(KEY_PRINCIPAL_SESSIONS + principal));

        Map<String, ?> fromIndex = redisIndexedSessionRepository.findByPrincipalName(principal);
        if (fromIndex != null) {
            sessionIds.addAll(fromIndex.keySet());
        }

        if (sessionIds.isEmpty()) {
            sessionIds.addAll(findSessionIdsByPrincipalScan(principal));
        }
        return sessionIds;
    }

    private Set<String> findSessionIdsByPrincipalScan(String principal) {
        Set<String> sessionIds = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match("spring:session:sessions:*").count(1000).build();
        try (Cursor<String> scan = stringRedisTemplate.scan(options)) {
            while (scan.hasNext()) {
                String key = scan.next();
                if (key.contains("spring:session:sessions:expires:")) {
                    continue;
                }
                if (!Boolean.TRUE.equals(stringRedisTemplate.opsForHash().hasKey(key, "sessionAttr:user"))) {
                    continue;
                }
                Object userObj = redisIndexedSessionRepository.getSessionRedisOperations()
                        .opsForHash().get(key, "sessionAttr:user");
                if (!(userObj instanceof SessionUser sessionUser)) {
                    continue;
                }
                if (!StringUtils.equals(principal, SessionUtils.buildPrincipalName(sessionUser))) {
                    continue;
                }
                sessionIds.add(key.substring(key.lastIndexOf(':') + 1));
            }
        } catch (Exception e) {
            log.error("按 principal 扫描 Session 失败 principal={}: {}", principal, e.getMessage(), e);
        }
        return sessionIds;
    }

    private void unregisterSession(String sessionId, String principal) {
        stringRedisTemplate.opsForSet().remove(KEY_PRINCIPAL_SESSIONS + principal, sessionId);
        if (sessionCountForPrincipal(principal) <= 0) {
            purgePrincipalRegistry(principal);
        }
    }

    private void purgePrincipalRegistry(String principal) {
        stringRedisTemplate.delete(KEY_PRINCIPAL_SESSIONS + principal);
        stringRedisTemplate.opsForSet().remove(KEY_LOCAL_PRINCIPALS, principal);
        stringRedisTemplate.opsForSet().remove(KEY_DS_PRINCIPALS, principal);
        stringRedisTemplate.opsForSet().remove(KEY_PLATFORM_PRINCIPALS, principal);
        String tenantId = parseTenantId(principal);
        if (tenantId != null) {
            stringRedisTemplate.opsForSet().remove(KEY_LOCAL_TENANT + tenantId, principal);
        }
    }

    private int sessionCountForPrincipal(String principal) {
        Set<String> registrySessionIds = members(KEY_PRINCIPAL_SESSIONS + principal);
        int active = countAndPruneRegistrySessions(principal, registrySessionIds);
        if (active > 0) {
            return active;
        }
        Map<String, ?> fromIndex = redisIndexedSessionRepository.findByPrincipalName(principal);
        if (fromIndex != null && !fromIndex.isEmpty()) {
            active = countLiveSessions(fromIndex.keySet());
        }
        if (active <= 0 && hasPrincipalRegistryTrace(principal, registrySessionIds)) {
            purgePrincipalRegistry(principal);
        }
        return active;
    }

    private int countAndPruneRegistrySessions(String principal, Set<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        int active = 0;
        for (String sessionId : sessionIds) {
            if (StringUtils.isBlank(sessionId)) {
                continue;
            }
            if (sessionExists(sessionId)) {
                active++;
            } else {
                stringRedisTemplate.opsForSet().remove(KEY_PRINCIPAL_SESSIONS + principal, sessionId);
                stringRedisTemplate.delete(KEY_SESSION_PRINCIPAL + sessionId);
            }
        }
        return active;
    }

    private int countLiveSessions(Set<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        int active = 0;
        for (String sessionId : sessionIds) {
            if (sessionExists(sessionId)) {
                active++;
            }
        }
        return active;
    }

    private boolean sessionExists(String sessionId) {
        String sessionKey = "spring:session:sessions:" + sessionId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(sessionKey))
                && Boolean.TRUE.equals(stringRedisTemplate.opsForHash().hasKey(sessionKey, "sessionAttr:user"));
    }

    private boolean hasPrincipalRegistryTrace(String principal, Set<String> registrySessionIds) {
        if (registrySessionIds != null && !registrySessionIds.isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PRINCIPAL_SESSIONS + principal))) {
            return true;
        }
        return members(KEY_LOCAL_PRINCIPALS).contains(principal)
                || members(KEY_DS_PRINCIPALS).contains(principal)
                || members(KEY_PLATFORM_PRINCIPALS).contains(principal);
    }

    private Set<String> members(String key) {
        Set<String> values = stringRedisTemplate.opsForSet().members(key);
        return values == null ? Collections.emptySet() : values;
    }

    private void touchTtl(String key) {
        stringRedisTemplate.expire(key, REGISTRY_TTL);
    }

    private static String normalizedSource(String source) {
        return StringUtils.defaultIfBlank(source, LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name());
    }

    private static String parseTenantId(String principal) {
        String[] parts = principal.split(":", 3);
        if (parts.length < 3) {
            return null;
        }
        if (!LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name().equalsIgnoreCase(parts[0])) {
            return null;
        }
        String tenantId = StringUtils.trimToNull(parts[1]);
        if (tenantId == null || PLACEHOLDER_TENANT.equals(tenantId)) {
            return null;
        }
        return tenantId;
    }

    private static <T> Set<T> difference(Set<T> left, Set<T> right) {
        Set<T> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
