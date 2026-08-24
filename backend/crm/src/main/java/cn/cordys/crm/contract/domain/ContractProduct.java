package cn.cordys.crm.contract.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Table(name = "contract_product")
public class ContractProduct extends BaseModel {

    @Schema(description = "合同ID")
    private String contractId;

    @Schema(description = "来源合同版本ID")
    private String sourceVersionId;

    @Schema(description = "行序号")
    private Integer sortNo;

    @Schema(description = "放款金额")
    private BigDecimal loanAmount;

    @Schema(description = "点位")
    private BigDecimal pointRate;

    @Schema(description = "应回款金额")
    private BigDecimal expectedRepaymentAmount;

    @Schema(description = "组织ID")
    private String organizationId;
}
