package cn.cordys.crm.contract.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Table(name = "contract_payment_record_product")
public class ContractPaymentRecordProduct extends BaseModel {

    @Schema(description = "回款记录ID")
    private String paymentRecordId;

    @Schema(description = "合同ID")
    private String contractId;

    @Schema(description = "签约人ID快照")
    private String signerId;

    @Schema(description = "签约人姓名快照")
    private String signerNameSnapshot;

    @Schema(description = "签约人部门ID快照")
    private String signerDeptIdSnapshot;

    @Schema(description = "签约人部门名快照")
    private String signerDeptNameSnapshot;

    @Schema(description = "客户来源快照")
    private String customerSourceSnapshot;

    @Schema(description = "来源回款版本ID")
    private String sourceVersionId;

    @Schema(description = "行序号")
    private Integer sortNo;

    @Schema(description = "放款时间")
    private Long loanTime;

    @Schema(description = "放款金额")
    private BigDecimal loanAmount;

    @Schema(description = "回款时间")
    private Long repaymentTime;

    @Schema(description = "回款金额")
    private BigDecimal repaymentAmount;

    @Schema(description = "成本金额")
    private BigDecimal costAmount;

    @Schema(description = "杂费金额")
    private BigDecimal miscFeeAmount;

    @Schema(description = "返佣金额")
    private BigDecimal commissionAmount;

    @Schema(description = "创收公式原文")
    private String revenueFormula;

    @Schema(description = "标准化创收公式")
    private String revenueFormulaNormalized;

    @Schema(description = "创收金额")
    private BigDecimal revenueAmount;

    @Schema(description = "组织ID")
    private String organizationId;
}
