package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerStageRollBackRequest {

    @Schema(description = "进行中回退设置")
    private Boolean afootRollBack;

    @Schema(description = "完结回退设置")
    private Boolean endRollBack;
}