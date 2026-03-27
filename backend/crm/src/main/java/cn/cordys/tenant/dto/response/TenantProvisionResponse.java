package cn.cordys.tenant.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantProvisionResponse {
    private String tenantId;
    private String dbName;
    private String jdbcUrl;
}
