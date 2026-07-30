package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerContractDeletePolicyResponse {

    @Schema(description = "合同删除策略：CASCADE、KEEP_CONTRACT")
    private String policy;
}
