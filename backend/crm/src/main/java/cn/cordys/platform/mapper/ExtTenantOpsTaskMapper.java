package cn.cordys.platform.mapper;

import cn.cordys.platform.domain.TenantOpsTask;
import cn.cordys.platform.dto.response.PlatformTenantProvisionTaskResponse;
import org.apache.ibatis.annotations.Param;

public interface ExtTenantOpsTaskMapper {
    int insertTask(TenantOpsTask task);

    PlatformTenantProvisionTaskResponse selectProvisionTaskById(@Param("taskId") String taskId);

    PlatformTenantProvisionTaskResponse selectLatestRunningProvisionTask(@Param("tenantId") String tenantId);

    int updateTaskStatus(@Param("taskId") String taskId,
                         @Param("status") String status,
                         @Param("detail") String detail,
                         @Param("updateTime") long updateTime);
}
