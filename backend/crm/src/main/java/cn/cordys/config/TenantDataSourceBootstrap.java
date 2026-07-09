package cn.cordys.config;

import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.constants.TenantDataSourceKeys;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import cn.cordys.tenant.service.TenantJdbcResolver;
import cn.cordys.tenant.service.TenantMetaService;
import jakarta.annotation.Resource;
import org.flywaydb.core.Flyway;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;

import java.util.List;

@Component
public class TenantDataSourceBootstrap implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceBootstrap.class);
    private static final String DEFAULT_TENANT_ID = "default";

    @Resource
    private DynamicTenantRoutingDataSource tenantRoutingDataSource;

    @Resource
    private TenantMetaService tenantMetaService;

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private TenantJdbcResolver tenantJdbcResolver;

    @Resource
    private TenantHikariDataSourceFactory tenantHikariDataSourceFactory;

    @Value("${spring.flyway.locations:classpath:migration}")
    private String flywayLocations;

    @Value("${spring.flyway.table:cordys_crm_version}")
    private String flywayTable;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean flywayBaselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String flywayBaselineVersion;

    @Value("${spring.flyway.encoding:UTF-8}")
    private String flywayEncoding;

    @Value("${spring.flyway.validate-on-migrate:false}")
    private boolean flywayValidateOnMigrate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("TenantDataSourceBootstrap start (order={})", getOrder());
        List<TenantDbConfigDTO> configs = tenantMetaService.listEnabledTenantDbConfigs();
        if (configs == null || configs.isEmpty()) {
            log.warn("TenantDataSourceBootstrap: no ACTIVE tenants found in master (tenant.status=ACTIVE)");
        }
        for (TenantDbConfigDTO config : configs) {
            String tenantId = config.getTenantId();
            if (StringUtils.isBlank(tenantId)) {
                continue;
            }

            // 启动时确保已启用租户的业务表（如 worker_node）已就绪
            // 避免启动期 UID/任务初始化因为表未创建而报错。
            try {
                log.info("Tenant schema migrate start, tenantId={}, jdbcUrl={}", tenantId, config.getJdbcUrl());
                migrateTenantSchema(config);
                log.info("Tenant schema migrate done, tenantId={}", tenantId);
            } catch (Exception e) {
                log.error("migrate tenant schema failed, tenantId={}", tenantId, e);
            }

            // default 租户的数据源已作为 routingDataSource 的 defaultTargetDataSource 初始化，无需重复注册
            if (DEFAULT_TENANT_ID.equals(tenantId)) {
                continue;
            }

            if (tenantRoutingDataSource.hasTenantDataSource(tenantId)) {
                continue;
            }
            try {
                tenantRoutingDataSource.registerTenantDataSource(
                        tenantId,
                        tenantHikariDataSourceFactory.createTenantPool(
                                config.getDriverClassName(),
                                config.getJdbcUrl(),
                                config.getDbUsername(),
                                config.getDbPassword(),
                                tenantId
                        )
                );
            } catch (Exception e) {
                log.error("register tenant datasource on startup failed, tenantId={}", tenantId, e);
            }
        }
        registerShadowDataSources();
        log.info("TenantDataSourceBootstrap done");
    }

    private void registerShadowDataSources() {
        List<String> shadowTenantIds = extTenantMapper.listShadowEnabledTenantIds();
        if (shadowTenantIds == null || shadowTenantIds.isEmpty()) {
            return;
        }
        for (String tenantId : shadowTenantIds) {
            if (StringUtils.isBlank(tenantId) || DEFAULT_TENANT_ID.equals(tenantId)) {
                continue;
            }
            String shadowKey = TenantDataSourceKeys.shadow(tenantId);
            if (tenantRoutingDataSource.hasTenantDataSource(shadowKey)) {
                continue;
            }
            try {
                TenantDbConfigDTO shadowConfig = tenantJdbcResolver.resolveShadowConnection(tenantId);
                migrateTenantSchema(shadowConfig);
                tenantRoutingDataSource.registerTenantDataSource(
                        shadowKey,
                        tenantHikariDataSourceFactory.createTenantPool(
                                shadowConfig.getDriverClassName(),
                                shadowConfig.getJdbcUrl(),
                                shadowConfig.getDbUsername(),
                                shadowConfig.getDbPassword(),
                                shadowKey
                        )
                );
                log.info("Shadow datasource registered, tenantId={}, dbName={}", tenantId, shadowConfig.getDbName());
            } catch (Exception e) {
                log.error("register shadow datasource on startup failed, tenantId={}", tenantId, e);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void migrateTenantSchema(TenantDbConfigDTO config) {
        // Flyway 本次迁移直接使用租户库连接信息，不依赖当前 ThreadLocal 的 TenantContext
        Flyway flyway = Flyway.configure()
                .dataSource(config.getJdbcUrl(), config.getDbUsername(), config.getDbPassword())
                .locations(resolveLocations())
                .encoding(flywayEncoding)
                .table(flywayTable)
                .baselineOnMigrate(flywayBaselineOnMigrate)
                .baselineVersion(flywayBaselineVersion)
                .validateOnMigrate(flywayValidateOnMigrate)
                .load();
        flyway.migrate();
    }

    private String[] resolveLocations() {
        String[] locations = flywayLocations.split(",");
        for (int i = 0; i < locations.length; i++) {
            locations[i] = locations[i] == null ? null : locations[i].trim();
        }
        return locations;
    }
}
