package cn.cordys.platform.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformTenantProvisionTaskResponse {
    private String taskId;
    private String tenantId;
    private String status;
    private String detail;
    private String operatorId;
    private Long createTime;
    private Long updateTime;
}
