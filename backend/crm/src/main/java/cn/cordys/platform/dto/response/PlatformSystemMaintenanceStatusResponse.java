package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlatformSystemMaintenanceStatusResponse {

    @Schema(description = "是否处于维护排水模式")
    private boolean maintenanceMode;

    @Schema(description = "在线用户总数（去重 principal）")
    private long onlineUserTotal;

    @Schema(description = "租户侧在线用户数")
    private long onlineTenantUserTotal;

    @Schema(description = "数据专员在线用户数")
    private long onlineDataSpecialistUserCount;
}
