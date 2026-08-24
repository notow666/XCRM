package cn.cordys.crm.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContractPaymentRecordApprovalRequest {

    @NotBlank
    @Size(max = 32)
    private String id;

    @NotBlank
    @Schema(description = "APPROVED/UNAPPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String approvalStatus;

    @Size(max = 1000)
    private String opinion;
}
