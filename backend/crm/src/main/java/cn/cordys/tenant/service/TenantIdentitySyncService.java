package cn.cordys.tenant.service;

import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 切换前将主库 A 的 sys_* 身份表增量同步至备用库 B。
 */
@Slf4j
@Service
public class TenantIdentitySyncService {

    private static final List<String> IDENTITY_TABLES = List.of(
            "sys_organization",
            "sys_organization_config",
            "sys_organization_config_detail",
            "sys_organization_user",
            "sys_department",
            "sys_department_commander",
            "sys_role",
            "sys_role_permission",
            "sys_role_scope_dept",
            "sys_user",
            "sys_user_extend",
            "sys_user_role"
    );

    @Resource
    private TenantJdbcResolver tenantJdbcResolver;

    public void incrementalSyncToShadow(String tenantId) {
        tenantId = StringUtils.trimToNull(tenantId);
        if (tenantId == null) {
            return;
        }
        TenantDbConfigDTO primary = tenantJdbcResolver.resolveConnection(tenantId);
        TenantDbConfigDTO shadow = tenantJdbcResolver.resolveShadowConnection(tenantId);
        JdbcTemplate primaryJdbc = jdbcTemplate(primary);
        JdbcTemplate shadowJdbc = jdbcTemplate(shadow);

        String previousTenant = TenantContext.getTenantId();
        RoutingPurpose previousPurpose = RoutingContext.getPurpose();
        try {
            TenantContext.setTenantId(tenantId);
            RoutingContext.setPurpose(RoutingPurpose.PRODUCTION_MAINTAIN);
            for (String table : IDENTITY_TABLES) {
                syncTable(primaryJdbc, shadowJdbc, primary.getDbName(), shadow.getDbName(), table);
            }
            log.info("[TENANT_IDENTITY_SYNC_DONE] tenantId={}, tables={}", tenantId, IDENTITY_TABLES.size());
        } finally {
            if (StringUtils.isBlank(previousTenant)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
            if (previousPurpose == null) {
                RoutingContext.clear();
            } else {
                RoutingContext.setPurpose(previousPurpose);
            }
        }
    }

    private void syncTable(JdbcTemplate primaryJdbc, JdbcTemplate shadowJdbc,
                           String primaryDb, String shadowDb, String table) {
        if (!tableExists(primaryJdbc, primaryDb, table) || !tableExists(shadowJdbc, shadowDb, table)) {
            log.warn("Skip identity sync, table missing: {}", table);
            return;
        }
        if (!hasUpdateTimeColumn(primaryJdbc, primaryDb, table)) {
            fullReplace(primaryJdbc, shadowJdbc, primaryDb, shadowDb, table);
            return;
        }
        String sql = """
                INSERT INTO `%s`.`%s`
                SELECT p.*
                FROM `%s`.`%s` p
                LEFT JOIN `%s`.`%s` s ON p.id = s.id
                WHERE s.id IS NULL OR p.update_time > s.update_time
                ON DUPLICATE KEY UPDATE
                """.formatted(shadowDb, table, primaryDb, table, shadowDb, table);
        List<String> columns = listColumns(primaryJdbc, primaryDb, table);
        String updateClause = columns.stream()
                .filter(c -> !"id".equalsIgnoreCase(c))
                .map(c -> "`" + c + "`=VALUES(`" + c + "`)")
                .reduce((a, b) -> a + "," + b)
                .orElse("id=id");
        shadowJdbc.update(sql + updateClause);
    }

    private void fullReplace(JdbcTemplate primaryJdbc, JdbcTemplate shadowJdbc,
                             String primaryDb, String shadowDb, String table) {
        shadowJdbc.update("DELETE FROM `" + shadowDb + "`.`" + table + "`");
        shadowJdbc.update("INSERT INTO `" + shadowDb + "`.`" + table + "` SELECT * FROM `" + primaryDb + "`.`" + table + "`");
    }

    private boolean tableExists(JdbcTemplate jdbc, String db, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class, db, table);
        return count != null && count > 0;
    }

    private boolean hasUpdateTimeColumn(JdbcTemplate jdbc, String db, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = 'update_time'",
                Integer.class, db, table);
        return count != null && count > 0;
    }

    private List<String> listColumns(JdbcTemplate jdbc, String db, String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                String.class, db, table);
    }

    private JdbcTemplate jdbcTemplate(TenantDbConfigDTO cfg) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(cfg.getDriverClassName());
        ds.setUrl(cfg.getJdbcUrl());
        ds.setUsername(cfg.getDbUsername());
        ds.setPassword(cfg.getDbPassword());
        return new JdbcTemplate(ds);
    }
}
