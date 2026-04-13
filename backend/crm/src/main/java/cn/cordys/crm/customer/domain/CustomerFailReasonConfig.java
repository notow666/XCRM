package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 客户失败原因配置
 */
@Data
@Table(name = "customer_fail_reason_config")
public class CustomerFailReasonConfig extends BaseModel {

    @Schema(description = "原因名称")
    private String name;

    @Schema(description = "顺序")
    private Long pos;

    @Schema(description = "组织id")
    private String organizationId;
}
