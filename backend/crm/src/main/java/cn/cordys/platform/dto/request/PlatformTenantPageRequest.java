package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PlatformTenantPageRequest {

    @Min(value = 1, message = "当前页码必须大于0")
    @Schema(description = "当前页码")
    private int current = 1;

    @Min(value = 1, message = "每页显示条数必须不小于1")
    @Max(value = 200, message = "每页显示条数不能大于200")
    @Schema(description = "每页显示条数")
    private int pageSize = 20;

    @Schema(description = "租户关键字（tenantId/code/name）")
    private String keyword;
}
