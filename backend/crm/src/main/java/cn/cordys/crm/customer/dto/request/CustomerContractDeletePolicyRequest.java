package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerContractDeletePolicyRequest {

    @NotBlank
    @Pattern(regexp = "CASCADE|KEEP_CONTRACT")
    @Schema(description = "合同删除策略：CASCADE、KEEP_CONTRACT")
    private String policy;
}
