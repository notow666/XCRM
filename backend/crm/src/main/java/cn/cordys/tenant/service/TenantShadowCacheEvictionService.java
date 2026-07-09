package cn.cordys.tenant.service;

import cn.cordys.common.util.JSON;
import cn.cordys.crm.system.notice.sse.SseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 切换时失效租户业务缓存（Redis 前缀 + Spring Cache 由 meta 失效触发重载）。
 */
@Slf4j
@Service
public class TenantShadowCacheEvictionService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TenantShadowMetaService tenantShadowMetaService;

    @Resource
    private SseService sseService;

    public void evictTenantBusinessCache(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        String prefix = tenantId.trim() + ":";
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("[TENANT_CACHE_EVICT] tenantId={}, redisKeys={}", tenantId, keys.size());
        }
        tenantShadowMetaService.evictShadowMetaCache(tenantId);
    }

    public void broadcastPreNotice(String tenantId) {
        broadcastTenantEvent(tenantId, "SHADOW_PRE_NOTICE",
                "系统即将更新，30s 后暂停使用，请在当前页面静待");
    }

    public void broadcastSwitchComplete(String tenantId) {
        broadcastTenantEvent(tenantId, "SHADOW_SWITCH_COMPLETE",
                "系统已更新，请刷新后使用");
    }

    private void broadcastTenantEvent(String tenantId, String eventType, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", eventType);
        payload.put("message", message);
        payload.put("tenantId", tenantId);
        String json = JSON.toJSONString(payload);
        sseService.broadcastTenantEvent(tenantId, json);
    }
}
