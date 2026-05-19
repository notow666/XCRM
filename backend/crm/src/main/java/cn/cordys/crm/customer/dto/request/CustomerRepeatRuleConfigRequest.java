package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerRepeatRuleConfigRequest {

    @NotNull
    @Schema(description = "是否启用重复规则", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @NotNull
    @Min(1)
    @Max(365)
    @Schema(description = "允许重复前的天数阈值", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer repeatAfterDays;
}
