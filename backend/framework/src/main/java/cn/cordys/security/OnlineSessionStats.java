package cn.cordys.security;

import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 在线会话统计快照（由 {@link OnlineSessionRegistry} 维护）。
 */
@Data
public class OnlineSessionStats {
    private long onlineUserTotal;
    private long onlineTenantUserTotal;
    private long onlineMultiDeviceUserCount;
    private long onlinePlatformUserCount;
    private long onlineDataSpecialistUserCount;
    private Map<String, Set<String>> localPrincipalsByTenant = new HashMap<>();
    private Map<String, Integer> sessionCountByPrincipal = new HashMap<>();
    private Set<String> tenantAndDataSpecialistPrincipals = new HashSet<>();
}
