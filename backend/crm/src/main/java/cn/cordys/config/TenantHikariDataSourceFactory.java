package cn.cordys.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 统一创建租户路由数据源使用的 HikariCP 连接池。
 */
@Component
@EnableConfigurationProperties(TenantHikariProperties.class)
public class TenantHikariDataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(TenantHikariDataSourceFactory.class);

    @Resource
    private TenantHikariProperties properties;

    public HikariDataSource create(String driverClassName,
                                   String url,
                                   String username,
                                   String password,
                                   String poolName) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setPoolName(poolName);
        dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
        dataSource.setMinimumIdle(properties.getMinimumIdle());
        dataSource.setConnectionTimeout(properties.getConnectionTimeout());
        dataSource.setIdleTimeout(properties.getIdleTimeout());
        dataSource.setMaxLifetime(properties.getMaxLifetime());
        dataSource.setConnectionTestQuery(properties.getConnectionTestQuery());
        log.info("Tenant Hikari pool created: poolName={}, maximumPoolSize={}, minimumIdle={}",
                poolName, properties.getMaximumPoolSize(), properties.getMinimumIdle());
        return dataSource;
    }

    public DataSource createTenantPool(String driverClassName,
                                       String url,
                                       String username,
                                       String password,
                                       String tenantId) {
        return create(driverClassName, url, username, password, "CordysTenant-" + tenantId);
    }
}
