package cn.cordys.config;

import cn.cordys.context.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支持启动时初始化与运行时动态注册租户数据源。
 */
public class DynamicTenantRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicTenantRoutingDataSource.class);

    private final Map<Object, Object> tenantTargets = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantIdOrDefault();
    }

    /**
     * 启动时一次性初始化（含 default 与各租户）。
     */
    public synchronized void initTargets(Map<Object, Object> initial) {
        tenantTargets.clear();
        tenantTargets.putAll(initial);
        setTargetDataSources(new HashMap<>(tenantTargets));
        afterPropertiesSet();
    }

    /**
     * 运行时注册新租户数据源（建库并完成 Flyway 后调用）。
     */
    public synchronized void registerTenantDataSource(String tenantId, DataSource dataSource) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataSource, "dataSource");
        tenantTargets.put(tenantId, dataSource);
        setTargetDataSources(new HashMap<>(tenantTargets));
        afterPropertiesSet();
    }

    /**
     * 从路由中移除并关闭连接池（用于开通失败回滚或运维下线租户）。
     */
    public synchronized void unregisterTenantDataSource(String tenantId) {
        if (tenantId == null) {
            return;
        }
        Object removed = tenantTargets.remove(tenantId);
        if (removed instanceof AutoCloseable) {
            try {
                ((AutoCloseable) removed).close();
            } catch (Exception e) {
                log.warn("close tenant datasource failed: {}", tenantId, e);
            }
        }
        setTargetDataSources(new HashMap<>(tenantTargets));
        afterPropertiesSet();
    }

    public boolean hasTenantDataSource(String tenantId) {
        return tenantTargets.containsKey(tenantId);
    }
}
