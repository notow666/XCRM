package cn.cordys.platform.service;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.security.OnlineSessionRegistry;
import cn.cordys.security.OnlineSessionStats;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class PlatformSessionOverviewService {

    private static final String PRINCIPAL_INDEX_PREFIX =
            "spring:session:index:" + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    /**
     * 在线统计：优先读注册表（O(在线人数)），其次 Spring Session principal 索引，最后全量 Session 扫描兜底。
     */
    public SessionOnlineSnapshot scanOnlineSessions() {
        OnlineSessionRegistry registry = CommonBeanFactory.getBean(OnlineSessionRegistry.class);
        if (registry != null) {
            OnlineSessionStats stats = registry.snapshot();
            if (stats.getOnlineUserTotal() > 0) {
                return fromStats(stats);
            }
        }

        SessionOnlineSnapshot fromIndex = scanFromPrincipalIndex();
        if (fromIndex.getOnlineUserTotal() > 0) {
            return fromIndex;
        }

        SessionOnlineSnapshot legacy = scanOnlineSessionsLegacy();
        if (legacy.getOnlineUserTotal() > 0) {
            return legacy;
        }
        return fromIndex;
    }

    /**
     * 全量 Session 扫描（兼容 principal 索引缺失的历史 Session）。
     */
    public SessionOnlineSnapshot scanOnlineSessionsLegacy() {
        Set<String> principals = new HashSet<>();
        Map<String, Integer> sessionCountByPrincipal = new HashMap<>();
        Map<String, Set<String>> localPrincipalsByTenant = new HashMap<>();
        Set<String> platformPrincipals = new HashSet<>();
        Set<String> dataSpecialistPrincipals = new HashSet<>();

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
                accumulateSessionUser(sessionUser, principals, sessionCountByPrincipal,
                        localPrincipalsByTenant, platformPrincipals, dataSpecialistPrincipals);
            }
        } catch (Exception e) {
            log.error("扫描在线会话失败: {}", e.getMessage(), e);
        }
        return buildSnapshot(principals, sessionCountByPrincipal, localPrincipalsByTenant,
                platformPrincipals, dataSpecialistPrincipals);
    }

    /**
     * 扫描 Spring Session principal 索引（仅含已登录用户，规模小于全量 Session）。
     */
    public SessionOnlineSnapshot scanFromPrincipalIndex() {
        Set<String> principals = new HashSet<>();
        Map<String, Integer> sessionCountByPrincipal = new HashMap<>();
        Map<String, Set<String>> localPrincipalsByTenant = new HashMap<>();
        Set<String> platformPrincipals = new HashSet<>();
        Set<String> dataSpecialistPrincipals = new HashSet<>();

        ScanOptions options = ScanOptions.scanOptions().match(PRINCIPAL_INDEX_PREFIX + "*").count(1000).build();
        try (Cursor<String> scan = stringRedisTemplate.scan(options)) {
            while (scan.hasNext()) {
                String key = scan.next();
                String principal = key.substring(PRINCIPAL_INDEX_PREFIX.length());
                if (StringUtils.isBlank(principal)) {
                    continue;
                }
                Long sessionCount = stringRedisTemplate.opsForSet().size(key);
                int count = sessionCount == null ? 0 : sessionCount.intValue();
                if (count <= 0) {
                    continue;
                }
                principals.add(principal);
                sessionCountByPrincipal.put(principal, count);
                classifyPrincipal(principal, localPrincipalsByTenant, platformPrincipals, dataSpecialistPrincipals);
            }
        } catch (Exception e) {
            log.error("扫描 principal 索引失败: {}", e.getMessage(), e);
        }
        return buildSnapshot(principals, sessionCountByPrincipal, localPrincipalsByTenant,
                platformPrincipals, dataSpecialistPrincipals);
    }

    public OnlineSessionStats toOnlineSessionStats(SessionOnlineSnapshot snapshot) {
        OnlineSessionStats stats = new OnlineSessionStats();
        stats.setOnlineUserTotal(snapshot.getOnlineUserTotal());
        stats.setOnlineTenantUserTotal(snapshot.getOnlineTenantUserTotal());
        stats.setOnlineMultiDeviceUserCount(snapshot.getOnlineMultiDeviceUserCount());
        stats.setOnlinePlatformUserCount(snapshot.getOnlinePlatformUserCount());
        stats.setOnlineDataSpecialistUserCount(snapshot.getOnlineDataSpecialistUserCount());
        stats.setLocalPrincipalsByTenant(snapshot.getLocalPrincipalsByTenant());
        stats.setSessionCountByPrincipal(snapshot.getSessionCountByPrincipal());

        Set<String> tenantAndDs = new HashSet<>();
        if (snapshot.getLocalPrincipalsByTenant() != null) {
            snapshot.getLocalPrincipalsByTenant().values().forEach(tenantAndDs::addAll);
        }
        if (snapshot.getSessionCountByPrincipal() != null) {
            snapshot.getSessionCountByPrincipal().keySet().stream()
                    .filter(p -> p.startsWith(LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name() + ":"))
                    .forEach(tenantAndDs::add);
        }
        stats.setTenantAndDataSpecialistPrincipals(tenantAndDs);
        return stats;
    }

    public SessionOnlineSnapshot fromStats(OnlineSessionStats stats) {
        SessionOnlineSnapshot snapshot = new SessionOnlineSnapshot();
        snapshot.setOnlineUserTotal(stats.getOnlineUserTotal());
        snapshot.setOnlineTenantUserTotal(stats.getOnlineTenantUserTotal());
        snapshot.setOnlineMultiDeviceUserCount(stats.getOnlineMultiDeviceUserCount());
        snapshot.setOnlinePlatformUserCount(stats.getOnlinePlatformUserCount());
        snapshot.setOnlineDataSpecialistUserCount(stats.getOnlineDataSpecialistUserCount());
        snapshot.setLocalPrincipalsByTenant(stats.getLocalPrincipalsByTenant());
        snapshot.setSessionCountByPrincipal(stats.getSessionCountByPrincipal());
        return snapshot;
    }

    private void accumulateSessionUser(SessionUser sessionUser,
                                       Set<String> principals,
                                       Map<String, Integer> sessionCountByPrincipal,
                                       Map<String, Set<String>> localPrincipalsByTenant,
                                       Set<String> platformPrincipals,
                                       Set<String> dataSpecialistPrincipals) {
        String principal = SessionUtils.buildPrincipalName(sessionUser);
        if (StringUtils.isBlank(principal)) {
            return;
        }
        principals.add(principal);
        sessionCountByPrincipal.merge(principal, 1, Integer::sum);
        classifyPrincipal(principal, localPrincipalsByTenant, platformPrincipals, dataSpecialistPrincipals);
    }

    private void classifyPrincipal(String principal,
                                   Map<String, Set<String>> localPrincipalsByTenant,
                                   Set<String> platformPrincipals,
                                   Set<String> dataSpecialistPrincipals) {
        String[] parts = principal.split(":", 3);
        if (parts.length < 3) {
            return;
        }
        String source = parts[0];
        if (LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name().equalsIgnoreCase(source)) {
            String tenantId = StringUtils.trimToNull(parts[1]);
            if (tenantId != null) {
                localPrincipalsByTenant.computeIfAbsent(tenantId, k -> new HashSet<>()).add(principal);
            }
        } else if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(source)) {
            platformPrincipals.add(principal);
        } else if (LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name().equalsIgnoreCase(source)) {
            dataSpecialistPrincipals.add(principal);
        }
    }

    private SessionOnlineSnapshot buildSnapshot(Set<String> principals,
                                                Map<String, Integer> sessionCountByPrincipal,
                                                Map<String, Set<String>> localPrincipalsByTenant,
                                                Set<String> platformPrincipals,
                                                Set<String> dataSpecialistPrincipals) {
        SessionOnlineSnapshot snapshot = new SessionOnlineSnapshot();
        snapshot.setOnlineUserTotal(principals.size());
        snapshot.setOnlinePlatformUserCount(platformPrincipals.size());
        snapshot.setOnlineDataSpecialistUserCount(dataSpecialistPrincipals.size());
        snapshot.setLocalPrincipalsByTenant(localPrincipalsByTenant);
        snapshot.setSessionCountByPrincipal(sessionCountByPrincipal);

        Set<String> localPrincipals = new HashSet<>();
        for (Set<String> set : localPrincipalsByTenant.values()) {
            localPrincipals.addAll(set);
        }
        snapshot.setOnlineTenantUserTotal(localPrincipals.size());

        long multiDevice = sessionCountByPrincipal.values().stream().filter(c -> c != null && c > 1).count();
        snapshot.setOnlineMultiDeviceUserCount(multiDevice);
        return snapshot;
    }

    @Data
    public static class SessionOnlineSnapshot {
        private long onlineUserTotal;
        private long onlineTenantUserTotal;
        private long onlineMultiDeviceUserCount;
        private long onlinePlatformUserCount;
        private long onlineDataSpecialistUserCount;
        private Map<String, Set<String>> localPrincipalsByTenant = new HashMap<>();
        private Map<String, Integer> sessionCountByPrincipal = new HashMap<>();
    }
}
