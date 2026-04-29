package cn.cordys.tenant.service;

import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.domain.TenantDbConfig;
import cn.cordys.tenant.mapper.ExtTenantDbConfigMapper;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TenantMetaService {

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private ExtTenantDbConfigMapper extTenantDbConfigMapper;

    public List<TenantDbConfigDTO> listEnabledTenantDbConfigs() {
        return extTenantDbConfigMapper.listEnabledTenantDbConfigs()
                .stream()
                .map(this::toTenantDbConfigDTO)
                .collect(Collectors.toList());
    }

    public TenantDbConfigDTO getTenantDbConfig(String tenantId) {
        TenantDbConfig config = extTenantDbConfigMapper.selectByTenantId(tenantId);
        if (config == null) {
            return null;
        }
        return toTenantDbConfigDTO(config);
    }

    public Set<String> listEnabledTenantIds() {
        return extTenantDbConfigMapper.listEnabledTenantIds().stream().collect(Collectors.toSet());
    }

    public Map<String, String> listEnabledTenant() {
        return extTenantDbConfigMapper.listEnabledTenantWithOrgId()
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("org_id")),
                        row -> String.valueOf(row.get("id"))
                ));
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
     * 租户是否可用（tenant.status=ACTIVE 且 tenant_db_config.enabled=1）。
     */
    public boolean isTenantEnabled(String tenantId) {
        String status = extTenantMapper.selectStatusByTenantId(tenantId);
        TenantDbConfig config = extTenantDbConfigMapper.selectByTenantId(tenantId);
        return status != null && config != null
                && "ACTIVE".equalsIgnoreCase(status)
                && Boolean.TRUE.equals(config.getEnabled());
    }

    public void insertTenant(String id, String code, String name, String orgId, long now, String operatorId) {
        extTenantMapper.insertTenant(
                id, code, name, "ACTIVE", StringUtils.trimToNull(orgId), now, now, operatorId, operatorId
        );
    }

    public void insertTenantDbConfig(String id, String tenantId, String dbName, String jdbcUrl, String dbUsername,
                                     String dbPassword, String driverClassName, long now, String operatorId) {
        extTenantDbConfigMapper.insertTenantDbConfig(
                id, tenantId, dbName, jdbcUrl, dbUsername, dbPassword, driverClassName, true, now, now, operatorId, operatorId
        );
    }

    /**
     * 开通失败回滚：删除该租户在主库中的元数据（不含其它租户）。
     */
    public void deleteTenantMetadataForProvisionRollback(String tenantId) {
        if (StringUtils.isBlank(tenantId) || "default".equals(tenantId)) {
            return;
        }
        extTenantDbConfigMapper.deleteByTenantId(tenantId);
        extTenantMapper.deleteByTenantId(tenantId);
    }

    private TenantDbConfigDTO toTenantDbConfigDTO(TenantDbConfig config) {
        TenantDbConfigDTO dto = new TenantDbConfigDTO();
        dto.setTenantId(config.getTenantId());
        dto.setDbName(config.getDbName());
        dto.setJdbcUrl(config.getJdbcUrl());
        dto.setDbUsername(config.getDbUsername());
        dto.setDbPassword(config.getDbPassword());
        dto.setDriverClassName(config.getDbDriverClassName());
        dto.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        return dto;
    }
}

