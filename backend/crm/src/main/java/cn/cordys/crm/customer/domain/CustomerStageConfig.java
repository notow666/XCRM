package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_stage_config")
public class CustomerStageConfig extends BaseModel {

    @Schema(description = "阶段名称")
    private String name;

    @Schema(description = "类型: AFOOT(进行中) / END(完结)")
    private String type;

    @Schema(description = "赢单概率")
    private String rate;

    @Schema(description = "进行中回退设置")
    private Boolean afootRollBack;

    @Schema(description = "完结回退设置")
    private Boolean endRollBack;

    @Schema(description = "顺序")
    private Long pos;

    @Schema(description = "组织id")
    private String organizationId;

    @Schema(description = "是否固定节点: true-固定不可删除/编辑, false-可自定义")
    private Boolean isFixed;
}