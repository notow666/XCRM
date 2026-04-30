package cn.cordys.tenant.service;

import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.util.JdbcUrlUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

/**
 * 从主数据源模板 + tenantId 推导租户库连接信息（与开通租户时的命名规则一致）。
 */
@Component
public class TenantJdbcResolver {

    @Resource
    @Qualifier("dataSourceProperties")
    private DataSourceProperties dataSourceProperties;

    public static String tenantDatabaseName(String tenantId) {
        return "crm_tenant_" + tenantId.replace('-', '_');
    }

    public TenantDbConfigDTO resolveConnection(String tenantId) {
        String dbName = tenantDatabaseName(tenantId);
        String templateUrl = dataSourceProperties.determineUrl();
        String jdbcUrl = JdbcUrlUtils.replaceMysqlDatabase(templateUrl, dbName);
        TenantDbConfigDTO dto = new TenantDbConfigDTO();
        dto.setTenantId(tenantId);
        dto.setDbName(dbName);
        dto.setJdbcUrl(jdbcUrl);
        dto.setDbUsername(dataSourceProperties.determineUsername());
        dto.setDbPassword(dataSourceProperties.determinePassword());
        dto.setDriverClassName(dataSourceProperties.determineDriverClassName());
        return dto;
    }

    public void enrichPlatformTenantItem(PlatformTenantItemResponse item) {
        if (item == null || item.getTenantId() == null) {
            return;
        }
        TenantDbConfigDTO d = resolveConnection(item.getTenantId());
        item.setDbName(d.getDbName());
        item.setJdbcUrl(d.getJdbcUrl());
        item.setEnabled("ACTIVE".equalsIgnoreCase(item.getStatus()));
    }
}
