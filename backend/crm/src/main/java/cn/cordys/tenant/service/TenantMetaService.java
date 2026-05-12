package cn.cordys.tenant.service;

import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TenantMetaService {

    /** orgId -> tenantId，全局一份；走 Spring Redis 缓存默认 TTL，变更时主动失效 */
    public static final String CACHE_TENANT_ENABLED_ORG_MAP = "tenant_enabled_org_map";

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private TenantJdbcResolver tenantJdbcResolver;

    public List<TenantDbConfigDTO> listEnabledTenantDbConfigs() {
        return extTenantMapper.listActiveTenantIds()
                .stream()
                .map(tenantId -> {
                    TenantDbConfigDTO dto = tenantJdbcResolver.resolveConnection(tenantId);
                    dto.setEnabled(true);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 租户在主库存在时返回推导后的连接信息；enabled 与 tenant.status 一致。
     */
    public TenantDbConfigDTO getTenantDbConfig(String tenantId) {
        String status = extTenantMapper.selectStatusByTenantId(tenantId);
        if (status == null) {
            return null;
        }
        TenantDbConfigDTO dto = tenantJdbcResolver.resolveConnection(tenantId);
        dto.setEnabled("ACTIVE".equalsIgnoreCase(status));
        return dto;
    }

    public Set<String> listEnabledTenantIds() {
        return extTenantMapper.listActiveTenantIds().stream().collect(Collectors.toSet());
    }

    @Cacheable(cacheNames = CACHE_TENANT_ENABLED_ORG_MAP, key = "'all'", unless = "#result == null")
    public Map<String, String> listEnabledTenant() {
        return extTenantMapper.listEnabledTenantWithOrgId()
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("org_id")),
                        row -> String.valueOf(row.get("id"))
                ));
    }

    /**
     * 主库租户 org 映射变更后调用（平台侧更新状态/组织等不经 {@link #insertTenant} 的路径）。
     */
    @CacheEvict(cacheNames = CACHE_TENANT_ENABLED_ORG_MAP, allEntries = true)
    public void evictEnabledTenantOrgMapCache() {
        // Spring Cache 代理处理失效
    }

    public boolean existsTenantId(String tenantId) {
        Long count = extTenantMapper.countByTenantId(tenantId);
        return count != null && count > 0;
    }

    public boolean existsTenantCode(String code) {
        Long count = extTenantMapper.countByCode(code);
        return count != null && count > 0;
    }

    /**
     * 租户是否可用（tenant.status=ACTIVE）。
     */
    public boolean isTenantEnabled(String tenantId) {
        String status = extTenantMapper.selectStatusByTenantId(tenantId);
        return status != null && "ACTIVE".equalsIgnoreCase(status);
    }

    @CacheEvict(cacheNames = CACHE_TENANT_ENABLED_ORG_MAP, allEntries = true)
    public void insertTenant(String id, String code, String name, String orgId, long now, String operatorId) {
        extTenantMapper.insertTenant(
                id, code, name, "ACTIVE", StringUtils.trimToNull(orgId), now, now, operatorId, operatorId
        );
    }

    /**
     * 开通失败回滚：删除该租户在主库中的元数据（不含其它租户）。
     */
    @CacheEvict(cacheNames = CACHE_TENANT_ENABLED_ORG_MAP, allEntries = true)
    public void deleteTenantMetadataForProvisionRollback(String tenantId) {
        if (StringUtils.isBlank(tenantId) || "default".equals(tenantId)) {
            return;
        }
        extTenantMapper.deleteByTenantId(tenantId);
    }
}
