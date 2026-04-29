package cn.cordys.tenant.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.common.service.DataInitService;
import cn.cordys.config.DynamicTenantRoutingDataSource;
import cn.cordys.context.TenantContext;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.dto.response.TenantProvisionResponse;
import cn.cordys.tenant.util.JdbcUrlUtils;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 动态创建租户库、执行业务库 Flyway 迁移、写入 crm_master 并注册路由数据源。
 */
@Service
public class TenantProvisioningService {
    private static final Logger managementLog = LoggerFactory.getLogger("MANAGEMENT_CENTER_LOG");

    private static final Set<String> RESERVED_TENANT_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "default", "master", "system", "mysql",
            "information_schema", "performance_schema",
            "sys", "admin", "management"
    )));

    @Resource
    private DynamicTenantRoutingDataSource tenantRoutingDataSource;

    @Resource
    @Qualifier("dataSourceProperties")
    private DataSourceProperties dataSourceProperties;

    @Resource
    private TenantMetaService tenantMetaService;
    @Resource
    private DataInitService dataInitService;

    @Value("${spring.flyway.locations:classpath:migration}")
    private String flywayLocations;

    @Value("${spring.flyway.table:cordys_crm_version}")
    private String flywayTable;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean flywayBaselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String flywayBaselineVersion;

    /**
     * 开通失败时是否尝试 DROP DATABASE（需账号具备 DROP 权限）。
     */
    @Value("${cordys.tenant.provision.drop-database-on-failure:true}")
    private boolean dropDatabaseOnFailure;

    /**
     * 开通新租户：建库、迁移、主库元数据、注册连接池。
     *
     * @param tenantCode    租户编码（与路由 tenantId 一致，小写）
     * @param tenantName    显示名
     * @param operatorId    操作人
     * @param initialUserIds 可选，与操作人去重后插入
     */
    public synchronized TenantProvisionResponse provision(String tenantCode, String tenantName, String operatorId,
                                                          List<String> initialUserIds, String orgId) {
        String tenantId = tenantCode.trim().toLowerCase(Locale.ROOT);
        managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_BEGIN] tenantId={}, operator={}", tenantId, operatorId);
        if (RESERVED_TENANT_CODES.contains(tenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get("tenant.code.reserved"));
        }
        if (tenantMetaService.existsTenantId(tenantId) || tenantMetaService.existsTenantCode(tenantId)) {
            // 幂等：若租户已存在且具备可用配置，直接返回现有结果；否则按重复创建报错
            TenantDbConfigDTO existingConfig = tenantMetaService.getTenantDbConfig(tenantId);
            if (existingConfig != null && StringUtils.isNotBlank(existingConfig.getJdbcUrl())) {
                if (!tenantRoutingDataSource.hasTenantDataSource(tenantId)) {
                    DataSource pool = buildPooledDataSource(existingConfig.getJdbcUrl(), existingConfig.getDriverClassName(),
                            existingConfig.getDbUsername(), existingConfig.getDbPassword(), tenantId);
                    tenantRoutingDataSource.registerTenantDataSource(tenantId, pool);
                }
                initializeTenantData(tenantId);
                managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_IDEMPOTENT_HIT] tenantId={}", tenantId);
                return TenantProvisionResponse.builder()
                        .tenantId(tenantId)
                        .dbName(existingConfig.getDbName())
                        .jdbcUrl(existingConfig.getJdbcUrl())
                        .build();
            }
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get("tenant.already.exists"));
        }

        String dbName = "crm_tenant_" + tenantId.replace('-', '_');
        String templateUrl = dataSourceProperties.determineUrl();
        String driver = dataSourceProperties.determineDriverClassName();
        String jdbcUser = dataSourceProperties.determineUsername();
        String jdbcPassword = dataSourceProperties.determinePassword();

        String serverUrl = JdbcUrlUtils.mysqlUrlWithoutDatabase(templateUrl);

        boolean databaseCreated = false;
        try {
            createDatabaseIfNotExists(serverUrl, driver, jdbcUser, jdbcPassword, dbName);
            databaseCreated = true;

            String tenantJdbcUrl = JdbcUrlUtils.replaceMysqlDatabase(templateUrl, dbName);
            runTenantFlyway(tenantJdbcUrl, jdbcUser, jdbcPassword);

            long now = System.currentTimeMillis();
            String configRowId = IDGenerator.nextStr();
            tenantMetaService.insertTenant(tenantId, tenantId, tenantName, orgId, now, operatorId);
            tenantMetaService.insertTenantDbConfig(configRowId, tenantId, dbName, tenantJdbcUrl, jdbcUser, jdbcPassword,
                    driver, now, operatorId);

            DataSource pool = buildPooledDataSource(tenantJdbcUrl, driver, jdbcUser, jdbcPassword, tenantId);
            tenantRoutingDataSource.registerTenantDataSource(tenantId, pool);
            initializeTenantData(tenantId);

            managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_SUCCESS] tenantId={}, dbName={}", tenantId, dbName);

            return TenantProvisionResponse.builder()
                    .tenantId(tenantId)
                    .dbName(dbName)
                    .jdbcUrl(tenantJdbcUrl)
                    .build();
        } catch (Exception e) {
            cleanupAfterProvisionFailure(tenantId, dbName, serverUrl, driver, jdbcUser, jdbcPassword, databaseCreated);
            managementLog.error(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_FAILED] tenantId={}, error={}", tenantId, e.getMessage(), e);
            throw new GenericException(CrmHttpResultCode.FAILED, e);
        }
    }

    private List<String> normalizeInitialUserIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        if (raw.size() > 50) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get("tenant.initial.users.limit"));
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            String t = s.trim();
            if (t.length() > 64) {
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED,
                        Translator.get("tenant.initial.user.id.invalid"));
            }
            out.add(t);
        }
        return new ArrayList<>(out);
    }

    private void cleanupAfterProvisionFailure(String tenantId, String dbName, String serverUrl, String driver,
                                              String jdbcUser, String jdbcPassword, boolean databaseCreated) {
        try {
            tenantRoutingDataSource.unregisterTenantDataSource(tenantId);
        } catch (Exception e) {
            managementLog.warn(LogModule.MANAGEMENT_MARKER,"unregister tenant datasource failed: {}", tenantId, e);
        }
        try {
            tenantMetaService.deleteTenantMetadataForProvisionRollback(tenantId);
        } catch (Exception e) {
            managementLog.warn(LogModule.MANAGEMENT_MARKER,"rollback master tenant metadata failed: {}", tenantId, e);
        }
        if (dropDatabaseOnFailure && databaseCreated && isProvisionedDbName(dbName, tenantId)) {
            try {
                dropDatabaseIfExists(serverUrl, driver, jdbcUser, jdbcPassword, dbName);
            } catch (Exception e) {
                managementLog.warn(LogModule.MANAGEMENT_MARKER,"DROP DATABASE failed, manual cleanup may be needed: {}", dbName, e);
            }
        }
    }

    private static boolean isProvisionedDbName(String dbName, String tenantId) {
        if (dbName == null || tenantId == null) {
            return false;
        }
        return dbName.equals("crm_tenant_" + tenantId.replace('-', '_'));
    }

    private void createDatabaseIfNotExists(String serverUrl, String driver, String user, String password, String dbName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(java.util.Objects.requireNonNull(driver, "driverClassName"));
        ds.setUrl(serverUrl);
        ds.setUsername(user);
        ds.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        String sql = "CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci";
        jdbcTemplate.execute(sql);
    }

    private void dropDatabaseIfExists(String serverUrl, String driver, String user, String password, String dbName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(java.util.Objects.requireNonNull(driver, "driverClassName"));
        ds.setUrl(serverUrl);
        ds.setUsername(user);
        ds.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + dbName + "`");
    }

    private void runTenantFlyway(String tenantJdbcUrl, String user, String password) {
        String[] locations = Arrays.stream(flywayLocations.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
        if (locations.length == 0) {
            locations = new String[]{"classpath:migration"};
        }
        Flyway flyway = Flyway.configure()
                .dataSource(tenantJdbcUrl, user, password)
                .locations(locations)
                .encoding(StandardCharsets.UTF_8)
                .table(flywayTable)
                .baselineOnMigrate(flywayBaselineOnMigrate)
                .baselineVersion(flywayBaselineVersion)
                .validateOnMigrate(false)
                .load();
        flyway.migrate();
    }

    private DataSource buildPooledDataSource(String jdbcUrl, String driver, String user, String password, String tenantId) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driver)
                .url(jdbcUrl)
                .username(user)
                .password(password)
                .build();
        ds.setPoolName("CordysTenant-" + tenantId);
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30_000L);
        ds.setMaxLifetime(1_800_000L);
        ds.setIdleTimeout(300_000L);
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }

    /**
     * 初始化租户业务元数据（模块、表单、字段等）。
     * 这些数据不完全由 Flyway DML 提供，需要执行一次 DataInitService。
     */
    private void initializeTenantData(String tenantId) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            dataInitService.initOneTime();
        } finally {
            if (StringUtils.isBlank(previousTenantId)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
        }
    }
}
