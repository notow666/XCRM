package cn.cordys.platform.dto.response;

import lombok.Data;

@Data
public class PlatformTenantDataCleanupTaskResponse {

    private String taskId;

    private String tenantId;

    private String status;

    private String detail;

    private String operatorId;

    private Long createTime;

    private Long updateTime;
}
