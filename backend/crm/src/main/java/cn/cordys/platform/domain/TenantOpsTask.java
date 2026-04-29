package cn.cordys.platform.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "tenant_ops_task")
public class TenantOpsTask extends BaseModel {
    private String tenantId;
    private String taskType;
    private String status;
    private String detail;
    private String operatorId;
}
