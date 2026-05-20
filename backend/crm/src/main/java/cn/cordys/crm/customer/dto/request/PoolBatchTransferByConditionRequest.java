package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按筛选条件批量转移公海客户请求
 */
@Data
public class PoolBatchTransferByConditionRequest extends CustomerPageRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "目标公海池ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetPoolId;

    @NotNull
    @Positive
    @Schema(description = "本次转移数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer transferCount;
}
