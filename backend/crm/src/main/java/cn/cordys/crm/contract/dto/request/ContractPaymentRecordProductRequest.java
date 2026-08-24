package cn.cordys.crm.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ContractPaymentRecordProductRequest {

    @NotNull
    @Schema(description = "放款时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long loanTime;

    @NotNull
    @DecimalMin("0.00")
    @Schema(description = "放款金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal loanAmount;

    @NotNull
    @Schema(description = "回款时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long repaymentTime;

    @NotNull
    @DecimalMin("0.00")
    @Schema(description = "回款金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal repaymentAmount;

    @NotNull
    @DecimalMin("0.00")
    @Schema(description = "成本金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal costAmount;

    @NotNull
    @DecimalMin("0.00")
    @Schema(description = "杂费金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal miscFeeAmount;

    @NotNull
    @DecimalMin("0.00")
    @Schema(description = "返佣金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal commissionAmount;

    @NotBlank
    @Schema(description = "创收公式", requiredMode = Schema.RequiredMode.REQUIRED)
    private String revenueFormula;

    @Schema(description = "后端标准化公式")
    private String revenueFormulaNormalized;

    @Schema(description = "前端预览创收金额，后端忽略并重新计算")
    private BigDecimal revenueAmount;
}
