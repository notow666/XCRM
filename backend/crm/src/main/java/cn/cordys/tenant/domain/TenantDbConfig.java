package cn.cordys.tenant.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "tenant_db_config")
public class TenantDbConfig extends BaseModel {
    private String tenantId;
    private String dbName;
    private String jdbcUrl;
    private String dbUsername;
    private String dbPassword;
    private String dbDriverClassName;
    private Boolean enabled;
}
