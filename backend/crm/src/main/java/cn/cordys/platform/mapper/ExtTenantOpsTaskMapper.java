package cn.cordys.platform.mapper;

import cn.cordys.platform.domain.TenantOpsTask;
import cn.cordys.platform.dto.response.PlatformTenantDataCleanupTaskResponse;
import cn.cordys.platform.dto.response.PlatformTenantProvisionTaskResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtTenantOpsTaskMapper {
    int insertTask(TenantOpsTask task);

    PlatformTenantProvisionTaskResponse selectProvisionTaskById(@Param("taskId") String taskId);

    PlatformTenantProvisionTaskResponse selectLatestRunningProvisionTask(@Param("tenantId") String tenantId);

    int updateTaskStatus(@Param("taskId") String taskId,
                         @Param("status") String status,
                         @Param("detail") String detail,
                         @Param("updateTime") long updateTime);

    PlatformTenantDataCleanupTaskResponse selectDataCleanupTaskById(@Param("taskId") String taskId);

    PlatformTenantDataCleanupTaskResponse selectLatestRunningDataCleanupTask(@Param("tenantId") String tenantId);

    Long countDataCleanupTasks(@Param("tenantId") String tenantId,
                               @Param("status") String status);

    List<PlatformTenantDataCleanupTaskResponse> pageDataCleanupTasks(@Param("tenantId") String tenantId,
                                                                     @Param("status") String status,
                                                                     @Param("limit") int limit,
                                                                     @Param("offset") int offset);

    Long countRunningByTenantId(@Param("tenantId") String tenantId);
}
