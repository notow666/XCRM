package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class PlatformOverviewResponse {
    @Schema(description = "租户总数")
    private long tenantTotal;
    @Schema(description = "活跃租户数")
    private long tenantActive;
    @Schema(description = "冻结租户数")
    private long tenantFrozen;
    @Schema(description = "租户状态分布")
    private List<PlatformOverviewSeriesItem> tenantStatusSeries;

    @Schema(description = "全系统在线用户去重（含平台/数据专员/租户）")
    private long onlineUserTotal;
    @Schema(description = "租户 CRM 在线用户去重")
    private long onlineTenantUserTotal;
    @Schema(description = "多端同时在线用户数（同 principal 会话数>1）")
    private long onlineMultiDeviceUserCount;
    @Schema(description = "平台超管在线去重")
    private long onlinePlatformUserCount;
    @Schema(description = "数据专员在线去重")
    private long onlineDataSpecialistUserCount;

    @Schema(description = "各租户在线用户 Top10+其他")
    private List<PlatformOverviewSeriesItem> onlineByTenantSeries;
    @Schema(description = "活跃租户在线覆盖（有在线/无在线）")
    private List<PlatformOverviewSeriesItem> activeTenantOnlineCoverageSeries;
}
