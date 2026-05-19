package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerRepeatRuleConfigResponse {

    @Schema(description = "是否启用重复规则")
    private Boolean enabled;

    @Schema(description = "允许重复前的天数阈值")
    private Integer repeatAfterDays;
}
