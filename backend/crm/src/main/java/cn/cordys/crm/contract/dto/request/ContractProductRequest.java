package cn.cordys.crm.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ContractProductRequest {

    @NotNull
    @DecimalMin(value = "0.00")
    @Schema(description = "放款金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal loanAmount;

    @DecimalMin(value = "0.00")
    @Schema(description = "点位")
    private BigDecimal pointRate;

    @NotNull
    @DecimalMin(value = "0.00")
    @Schema(description = "应回款金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal expectedRepaymentAmount;
}
