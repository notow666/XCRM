package cn.cordys.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户业务库 HikariCP 连接池配置（各租户独立池共用同一套参数）。
 */
@Data
@ConfigurationProperties(prefix = "cordys.tenant.datasource.hikari")
public class TenantHikariProperties {

    private int maximumPoolSize = 25;
    private int minimumIdle = 2;
    private long connectionTimeout = 60_000L;
    private long idleTimeout = 300_000L;
    private long maxLifetime = 1_800_000L;
    private String connectionTestQuery = "SELECT 1";
    /**
     * 连接泄漏检测阈值，0表示关闭。
     */
    private long leakDetectionThreshold = 0L;
}
