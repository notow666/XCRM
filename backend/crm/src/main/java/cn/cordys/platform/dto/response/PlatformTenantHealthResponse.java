package cn.cordys.platform.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformTenantHealthResponse {
    private String tenantId;
    private boolean metadataExists;
    private boolean datasourceRegistered;
    private boolean jdbcReachable;
    private String migrationVersion;
}
