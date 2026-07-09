package cn.cordys.tenant.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataInitService;
import cn.cordys.common.util.Translator;
import cn.cordys.config.DynamicTenantRoutingDataSource;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.config.TenantHikariDataSourceFactory;
import cn.cordys.tenant.constants.TenantDataSourceKeys;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import cn.cordys.tenant.util.JdbcUrlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 为指定租户开通影子库（空库 + Flyway + 默认配置初始化），并注册 JDBC 路由。
 */
@Slf4j
@Service
public class TenantShadowProvisioningService {

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private TenantMetaService tenantMetaService;

    @Resource
    private TenantShadowMetaService tenantShadowMetaService;

    @Resource
    private DynamicTenantRoutingDataSource tenantRoutingDataSource;

    @Resource
    private TenantHikariDataSourceFactory tenantHikariDataSourceFactory;

    @Resource
    private TenantJdbcResolver tenantJdbcResolver;

    @Resource
    private DataInitService dataInitService;

    @Resource
    @Qualifier("dataSourceProperties")
    private DataSourceProperties dataSourceProperties;

    @Value("${spring.flyway.locations:classpath:migration}")
    private String flywayLocations;

    @Value("${spring.flyway.table:cordys_crm_version}")
    private String flywayTable;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean flywayBaselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String flywayBaselineVersion;

    public synchronized void enableShadow(String tenantId, String operatorId) {
        tenantId = StringUtils.trimToNull(tenantId);
        if (tenantId == null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "tenantId 不能为空");
        }
        if (!tenantMetaService.existsTenantId(tenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get("tenant.not.found"));
        }
        if (tenantShadowMetaService.isShadowEnabled(tenantId)) {
            ensureShadowDataSourceRegistered(tenantId);
            initializeShadowTenantData(tenantId);
            return;
        }

        String dbName = TenantJdbcResolver.shadowDatabaseName(tenantId);
        String templateUrl = dataSourceProperties.determineUrl();
        String driver = dataSourceProperties.determineDriverClassName();
        String jdbcUser = dataSourceProperties.determineUsername();
        String jdbcPassword = dataSourceProperties.determinePassword();
        String serverUrl = JdbcUrlUtils.mysqlUrlWithoutDatabase(templateUrl);

        createDatabaseIfNotExists(serverUrl, driver, jdbcUser, jdbcPassword, dbName);
        String shadowJdbcUrl = JdbcUrlUtils.replaceMysqlDatabase(templateUrl, dbName);
        runTenantFlyway(shadowJdbcUrl, jdbcUser, jdbcPassword);

        long now = System.currentTimeMillis();
        int updated = extTenantMapper.enableShadow(tenantId, now, operatorId);
        if (updated <= 0) {
            throw new GenericException(CrmHttpResultCode.FAILED, Translator.get("tenant.shadow.enable.failed"));
        }
        tenantShadowMetaService.evictShadowMetaCache(tenantId);
        registerShadowDataSource(tenantId);
        initializeShadowTenantData(tenantId);
        log.info("[TENANT_SHADOW_ENABLED] tenantId={}, dbName={}, operator={}", tenantId, dbName, operatorId);
    }

    /**
     * 初始化影子库默认配置（模块、表单、字段等），与主库开通逻辑一致，但不注册 Quartz。
     */
    private void initializeShadowTenantData(String tenantId) {
        String previousTenantId = TenantContext.getTenantId();
        RoutingPurpose previousPurpose = RoutingContext.getPurpose();
        try {
            TenantContext.setTenantId(tenantId);
            RoutingContext.setPurpose(RoutingPurpose.SHADOW_PROVISION);
            dataInitService.initOneTime();
        } finally {
            if (StringUtils.isBlank(previousTenantId)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
            if (previousPurpose == null) {
                RoutingContext.clear();
            } else {
                RoutingContext.setPurpose(previousPurpose);
            }
        }
    }

    public void ensureShadowDataSourceRegistered(String tenantId) {
        String shadowKey = TenantDataSourceKeys.shadow(tenantId);
        if (tenantRoutingDataSource.hasTenantDataSource(shadowKey)) {
            return;
        }
        registerShadowDataSource(tenantId);
    }

    private void registerShadowDataSource(String tenantId) {
        TenantDbConfigDTO cfg = tenantJdbcResolver.resolveShadowConnection(tenantId);
        String shadowKey = TenantDataSourceKeys.shadow(tenantId);
        DataSource pool = tenantHikariDataSourceFactory.createTenantPool(
                cfg.getDriverClassName(), cfg.getJdbcUrl(), cfg.getDbUsername(), cfg.getDbPassword(), shadowKey);
        tenantRoutingDataSource.registerTenantDataSource(shadowKey, pool);
    }

    private void createDatabaseIfNotExists(String serverUrl, String driver, String user, String password, String dbName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(driver);
        ds.setUrl(serverUrl);
        ds.setUsername(user);
        ds.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
    }

    private void runTenantFlyway(String tenantJdbcUrl, String user, String password) {
        String[] locations = Arrays.stream(flywayLocations.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
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
}
