package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


/**
 * 公海客户转移请求
 */
@Data
public class PoolTransferRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "目标公海池ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetPoolId;
}