package cn.cordys.platform.dto.request;

import lombok.Data;

@Data
public class PlatformTenantDataCleanupTaskPageRequest {

    private Integer current = 1;

    private Integer pageSize = 20;

    private String tenantId;

    private String status;
}
