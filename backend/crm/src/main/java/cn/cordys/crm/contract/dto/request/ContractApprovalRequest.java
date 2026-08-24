package cn.cordys.crm.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContractApprovalRequest {

    @NotBlank
    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;


    @NotBlank
    @Schema(description = "审核状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String approvalStatus;

    @Size(max = 1000)
    @Schema(description = "审批意见")
    private String opinion;
}
