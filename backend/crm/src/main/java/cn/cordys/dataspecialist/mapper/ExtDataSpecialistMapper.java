package cn.cordys.dataspecialist.mapper;

import cn.cordys.dataspecialist.domain.DataSpecialist;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminItemResponse;
import cn.cordys.dataspecialist.dto.DataSpecialistTenantOption;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtDataSpecialistMapper {

    int insert(DataSpecialist row);

    int update(DataSpecialist row);

    DataSpecialist selectByUsername(@Param("username") String username);

    DataSpecialist selectById(@Param("id") String id);

    Long countByKeyword(@Param("keyword") String keyword, @Param("like") String like);

    List<DataSpecialistAdminItemResponse> pageByKeyword(@Param("keyword") String keyword,
                                                        @Param("like") String like,
                                                        @Param("pageSize") int pageSize,
                                                        @Param("offset") int offset);

    int deleteTenantLinks(@Param("specialistId") String specialistId);

    int insertTenantLink(@Param("specialistId") String specialistId, @Param("tenantId") String tenantId);

    List<String> listTenantIds(@Param("specialistId") String specialistId);

    int existsTenantBinding(@Param("specialistId") String specialistId, @Param("tenantId") String tenantId);

    List<DataSpecialistTenantOption> listAllowedTenants(@Param("specialistId") String specialistId);

    Long countByUsernameExcludeId(@Param("username") String username, @Param("excludeId") String excludeId);
}
