package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerStageAddRequest {

    @Schema(description = "阶段名称")
    private String name;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "赢单概率")
    private String rate;

    @Schema(description = "添加的位置（取值：-1，,1。 -1：源节点之前，1：源节点之后）", requiredMode = Schema.RequiredMode.REQUIRED)
    private int dropPosition;

    @Schema(description = "源节点")
    private String targetId;
}