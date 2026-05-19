package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_repeat_rule_config")
public class CustomerRepeatRuleConfig extends BaseModel {

    @Schema(description = "是否启用重复规则")
    private Boolean enabled;

    @Schema(description = "允许重复前的天数阈值")
    private Integer repeatAfterDays;
}
