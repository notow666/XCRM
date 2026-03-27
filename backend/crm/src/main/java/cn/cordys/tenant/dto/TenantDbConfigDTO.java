package cn.cordys.tenant.dto;

import lombok.Data;

@Data
public class TenantDbConfigDTO {
    private String tenantId;
    private String dbName;
    private String jdbcUrl;
    private String dbUsername;
    private String dbPassword;
    private String driverClassName;
    private Boolean enabled;
}

