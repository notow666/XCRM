package cn.cordys.tenant.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "tenant")
public class Tenant extends BaseModel {
    private String code;
    private String name;
    private String status;
    private String orgId;
}
