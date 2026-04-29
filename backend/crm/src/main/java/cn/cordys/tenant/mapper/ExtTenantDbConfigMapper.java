package cn.cordys.tenant.mapper;

import cn.cordys.tenant.domain.TenantDbConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ExtTenantDbConfigMapper {
    int insertTenantDbConfig(@Param("id") String id,
                             @Param("tenantId") String tenantId,
                             @Param("dbName") String dbName,
                             @Param("jdbcUrl") String jdbcUrl,
                             @Param("dbUsername") String dbUsername,
                             @Param("dbPassword") String dbPassword,
                             @Param("dbDriverClassName") String dbDriverClassName,
                             @Param("enabled") boolean enabled,
                             @Param("createTime") long createTime,
                             @Param("updateTime") long updateTime,
                             @Param("createUser") String createUser,
                             @Param("updateUser") String updateUser);

    int deleteByTenantId(@Param("tenantId") String tenantId);

    List<TenantDbConfig> listEnabledTenantDbConfigs();

    TenantDbConfig selectByTenantId(@Param("tenantId") String tenantId);

    List<String> listEnabledTenantIds();

    int updateEnabledByTenantId(@Param("tenantId") String tenantId,
                                @Param("enabled") int enabled,
                                @Param("updateTime") long updateTime,
                                @Param("updateUser") String updateUser);

    List<Map<String, Object>> listEnabledTenantWithOrgId();
}
