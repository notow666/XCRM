package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerFollowWayResponse {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "方式名称")
    private String name;

    @Schema(description = "顺序")
    private Long pos;
}
