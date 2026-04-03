package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerStageRequest {

    @NotBlank
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @NotBlank
    @Schema(description = "客户阶段", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stage;

    @Schema(description = "失败原因")
    private String failureReason;
}