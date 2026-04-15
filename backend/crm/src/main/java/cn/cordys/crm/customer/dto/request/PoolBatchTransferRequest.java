package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;


/**
 * 公海客户批量转移请求
 */
@Data
public class PoolBatchTransferRequest {

    @NotEmpty
    @Schema(description = "客户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> batchIds;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "目标公海池ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetPoolId;
}