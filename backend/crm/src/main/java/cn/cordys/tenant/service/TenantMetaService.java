package cn.cordys.tenant.service;

import cn.cordys.tenant.dto.TenantDbConfigDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TenantMetaService {

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    public List<TenantDbConfigDTO> listEnabledTenantDbConfigs() {
        String sql = "SELECT tenant_id, db_name, jdbc_url, db_username, db_password, db_driver_class_name, enabled " +
                "FROM tenant_db_config WHERE enabled = 1";
        try {
            return masterJdbcTemplate.query(sql, (rs, rowNum) -> {
                TenantDbConfigDTO item = new TenantDbConfigDTO();
                item.setTenantId(rs.getString("tenant_id"));
                item.setDbName(rs.getString("db_name"));
                item.setJdbcUrl(rs.getString("jdbc_url"));
                item.setDbUsername(rs.getString("db_username"));
                item.setDbPassword(rs.getString("db_password"));
                item.setDriverClassName(rs.getString("db_driver_class_name"));
                item.setEnabled(rs.getBoolean("enabled"));
                return item;
            });
        } catch (DataAccessException e) {
            return java.util.Collections.emptyList();
        }
    }

    public TenantDbConfigDTO getTenantDbConfig(String tenantId) {
        String sql = "SELECT tenant_id, db_name, jdbc_url, db_username, db_password, db_driver_class_name, enabled " +
                "FROM tenant_db_config WHERE tenant_id = ? LIMIT 1";
        try {
            List<TenantDbConfigDTO> items = masterJdbcTemplate.query(sql, (rs, rowNum) -> {
                TenantDbConfigDTO item = new TenantDbConfigDTO();
                item.setTenantId(rs.getString("tenant_id"));
                item.setDbName(rs.getString("db_name"));
                item.setJdbcUrl(rs.getString("jdbc_url"));
                item.setDbUsername(rs.getString("db_username"));
                item.setDbPassword(rs.getString("db_password"));
                item.setDriverClassName(rs.getString("db_driver_class_name"));
                item.setEnabled(rs.getBoolean("enabled"));
                return item;
            }, tenantId);
            return items.isEmpty() ? null : items.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    public Set<String> listEnabledTenantIds() {
        String sql = "SELECT tenant_id FROM tenant_db_config WHERE enabled = 1";
        try {
            return masterJdbcTemplate.queryForList(sql, String.class).stream().collect(Collectors.toSet());
        } catch (DataAccessException e) {
            return java.util.Collections.emptySet();
        }
    }

    public Map<String, String> listEnabledTenant() {
        String sql = "SELECT id, org_id FROM tenant WHERE status = 'ACTIVE' and org_id is not null and org_id != ''";
        try {
            return masterJdbcTemplate.query(sql, (rs, rowNum) -> {
                return rs;
            }).stream().collect(Collectors.toMap(o -> {
                try {
                    return o.getString("org_id");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, o -> {
                try {
                    return o.getString("id");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }));
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    public boolean existsTenantId(String tenantId) {
        String sql = "SELECT COUNT(1) FROM tenant WHERE id = ?";
        try {
            Integer c = masterJdbcTemplate.queryForObject(sql, Integer.class, tenantId);
            return c != null && c > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    public boolean existsTenantCode(String code) {
        String sql = "SELECT COUNT(1) FROM tenant WHERE code = ?";
        try {
            Integer c = masterJdbcTemplate.queryForObject(sql, Integer.class, code);
            return c != null && c > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    /**
     * 租户是否可用（tenant.status=ACTIVE 且 tenant_db_config.enabled=1）。
     */
    public boolean isTenantEnabled(String tenantId) {
        String sql = "SELECT COUNT(1) FROM tenant t INNER JOIN tenant_db_config c ON c.tenant_id = t.id " +
                "WHERE t.id = ? AND t.status = 'ACTIVE' AND c.enabled = 1";
        try {
            Integer c = masterJdbcTemplate.queryForObject(sql, Integer.class, tenantId);
            return c != null && c > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    public void insertTenant(String id, String code, String name, long now, String operatorId) {
        String sql = "INSERT INTO tenant (id, code, name, status, create_time, update_time, create_user, update_user) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)";
        masterJdbcTemplate.update(sql, id, code, name, now, now, operatorId, operatorId);
    }

    public void insertTenantDbConfig(String id, String tenantId, String dbName, String jdbcUrl, String dbUsername,
                                     String dbPassword, String driverClassName, long now, String operatorId) {
        String sql = "INSERT INTO tenant_db_config (id, tenant_id, db_name, jdbc_url, db_username, db_password, "
                + "db_driver_class_name, enabled, create_time, update_time, create_user, update_user) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)";
        masterJdbcTemplate.update(sql, id, tenantId, dbName, jdbcUrl, dbUsername, dbPassword, driverClassName,
                now, now, operatorId, operatorId);
    }

    /**
     * 开通失败回滚：删除该租户在主库中的元数据（不含其它租户）。
     */
    public void deleteTenantMetadataForProvisionRollback(String tenantId) {
        if (StringUtils.isBlank(tenantId) || "default".equals(tenantId)) {
            return;
        }
        masterJdbcTemplate.update("DELETE FROM tenant_db_config WHERE tenant_id = ?", tenantId);
        masterJdbcTemplate.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }
}

