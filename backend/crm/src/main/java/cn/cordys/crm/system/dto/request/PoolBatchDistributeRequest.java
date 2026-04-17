package cn.cordys.crm.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PoolBatchDistributeRequest extends PoolBatchRequest {

    @NotBlank
    @Schema(description = "目标公海池ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerPoolId;
}
