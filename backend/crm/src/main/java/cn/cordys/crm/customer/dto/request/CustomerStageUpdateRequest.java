package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerStageUpdateRequest {

    @Schema(description = "id")
    private String id;

    @Schema(description = "阶段名称")
    private String name;

    @Schema(description = "赢单概率")
    private String rate;
}