package cn.cordys.platform.dto.response;

import lombok.Data;

@Data
public class PlatformTenantItemResponse {
    private String tenantId;
    private String code;
    private String name;
    private String status;
    private String orgId;
    private String dbName;
    private String jdbcUrl;
    private Boolean enabled;
    private Long createTime;
    private Long updateTime;
}
