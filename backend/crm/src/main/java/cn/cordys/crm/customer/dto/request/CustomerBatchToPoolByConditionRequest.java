package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CustomerBatchToPoolByConditionRequest extends CustomerPageRequest {

    @Schema(description = "目标公海池ID")
    private String targetPoolId;

    @Schema(description = "原因")
    private String reasonId;

    @Schema(description = "本次移入数量")
    private Integer moveCount;
}
