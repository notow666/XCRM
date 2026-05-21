package cn.cordys.platform.service;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class PlatformSessionOverviewService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    public SessionOnlineSnapshot scanOnlineSessions() {
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
                if (!(userObj instanceof SessionUser)) {
                    continue;
                }
                SessionUser sessionUser = (SessionUser) userObj;
                String principal = SessionUtils.buildPrincipalName(sessionUser);
                if (StringUtils.isBlank(principal)) {
                    continue;
                }
                principals.add(principal);
                sessionCountByPrincipal.merge(principal, 1, Integer::sum);

                String source = StringUtils.defaultIfBlank(sessionUser.getSource(),
                        LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name());
                if (LoginAuthenticateConstants.LoginAuthenticateType.LOCAL.name().equalsIgnoreCase(source)) {
                    String tenantId = StringUtils.trimToNull(sessionUser.getTenantId());
                    if (tenantId != null) {
                        localPrincipalsByTenant.computeIfAbsent(tenantId, k -> new HashSet<>()).add(principal);
                    }
                } else if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(source)) {
                    platformPrincipals.add(principal);
                } else if (LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name().equalsIgnoreCase(source)) {
                    dataSpecialistPrincipals.add(principal);
                }
            }
        } catch (Exception e) {
            log.error("扫描在线会话失败: {}", e.getMessage(), e);
        }

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
