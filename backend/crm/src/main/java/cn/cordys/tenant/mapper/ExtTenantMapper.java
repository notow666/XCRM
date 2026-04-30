package cn.cordys.tenant.mapper;

import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ExtTenantMapper {
    int insertTenant(@Param("id") String id,
                     @Param("code") String code,
                     @Param("name") String name,
                     @Param("status") String status,
                     @Param("orgId") String orgId,
                     @Param("createTime") long createTime,
                     @Param("updateTime") long updateTime,
                     @Param("createUser") String createUser,
                     @Param("updateUser") String updateUser);

    int deleteByTenantId(@Param("tenantId") String tenantId);

    Long countByTenantId(@Param("tenantId") String tenantId);

    Long countByCode(@Param("code") String code);

    String selectStatusByTenantId(@Param("tenantId") String tenantId);

    Long countByKeyword(@Param("keyword") String keyword, @Param("like") String like);

    List<PlatformTenantItemResponse> pageByKeyword(@Param("keyword") String keyword,
                                                   @Param("like") String like,
                                                   @Param("pageSize") int pageSize,
                                                   @Param("offset") int offset);

    PlatformTenantItemResponse selectDetailByTenantId(@Param("tenantId") String tenantId);

    int updateTenantStatus(@Param("tenantId") String tenantId,
                           @Param("status") String status,
                           @Param("updateTime") long updateTime,
                           @Param("updateUser") String updateUser);

    int updateTenantOrgId(@Param("tenantId") String tenantId,
                          @Param("orgId") String orgId,
                          @Param("updateTime") long updateTime,
                          @Param("updateUser") String updateUser);

    List<String> listActiveTenantIds();

    List<Map<String, Object>> listEnabledTenantWithOrgId();
}
