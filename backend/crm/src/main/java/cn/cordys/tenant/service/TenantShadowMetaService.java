package cn.cordys.tenant.service;

import cn.cordys.context.ActiveDbRole;
import cn.cordys.context.ShadowMaintenanceState;
import cn.cordys.tenant.dto.TenantShadowMetaDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TenantShadowMetaService {

    public static final String CACHE_TENANT_SHADOW_META = "tenant_shadow_meta";

    @Resource
    private ExtTenantMapper extTenantMapper;

    private final ConcurrentMap<String, TenantShadowMetaDTO> localCache = new ConcurrentHashMap<>();

    @Cacheable(cacheNames = CACHE_TENANT_SHADOW_META, key = "#tenantId", unless = "#result == null")
    public TenantShadowMetaDTO getShadowMeta(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return null;
        }
        TenantShadowMetaDTO dto = extTenantMapper.selectShadowMetaByTenantId(tenantId.trim());
        if (dto == null) {
            return null;
        }
        normalize(dto);
        return dto;
    }

    public boolean isShadowEnabled(String tenantId) {
        TenantShadowMetaDTO meta = getShadowMeta(tenantId);
        return meta != null && meta.isShadowEnabled();
    }

    public boolean isShadowActive(String tenantId) {
        TenantShadowMetaDTO meta = getShadowMeta(tenantId);
        return meta != null && meta.isShadowEnabled() && meta.getActiveDbRole() == ActiveDbRole.SHADOW;
    }

    public boolean isInMaintenanceBlocking(String tenantId) {
        TenantShadowMetaDTO meta = getShadowMeta(tenantId);
        if (meta == null || meta.getMaintenanceState() != ShadowMaintenanceState.BLOCKING) {
            return false;
        }
        Long until = meta.getMaintenanceUntil();
        return until == null || until > System.currentTimeMillis();
    }

    @CacheEvict(cacheNames = CACHE_TENANT_SHADOW_META, key = "#tenantId")
    public void evictShadowMetaCache(String tenantId) {
        if (StringUtils.isNotBlank(tenantId)) {
            localCache.remove(tenantId.trim());
        }
    }

    public void refreshLocalShadowMeta(String tenantId) {
        evictShadowMetaCache(tenantId);
        TenantShadowMetaDTO fresh = extTenantMapper.selectShadowMetaByTenantId(tenantId);
        if (fresh != null) {
            normalize(fresh);
            localCache.put(tenantId, fresh);
        }
    }

    private static void normalize(TenantShadowMetaDTO dto) {
        if (dto.getActiveDbRole() == null) {
            dto.setActiveDbRole(ActiveDbRole.PRIMARY);
        }
    }
}
