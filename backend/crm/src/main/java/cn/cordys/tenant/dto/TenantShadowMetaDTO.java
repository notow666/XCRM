package cn.cordys.tenant.dto;

import cn.cordys.context.ActiveDbRole;
import cn.cordys.context.ShadowMaintenanceState;
import lombok.Data;

@Data
public class TenantShadowMetaDTO {
    private String tenantId;
    private boolean shadowEnabled;
    private ActiveDbRole activeDbRole;
    private ShadowMaintenanceState maintenanceState;
    private Long maintenanceUntil;
}
