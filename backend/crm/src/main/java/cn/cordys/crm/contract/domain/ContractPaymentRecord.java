package cn.cordys.crm.contract.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同回款记录
 * @author song-cc-rock
 */

@Data
@Table(name = "contract_payment_record")
public class ContractPaymentRecord extends BaseModel {

	@Schema(description = "回款记录名称")
	private String name;

	@Schema(description = "回款编号")
	private String no;

	@Schema(description = "负责人")
	private String owner;

	@Schema(description = "合同ID")
	private String contractId;

	@Schema(description = "回款计划ID")
	private String paymentPlanId;

	@Schema(description = "审批状态")
	private String approvalStatus;

	@Schema(description = "当前生效版本ID")
	private String effectiveVersionId;

	@Schema(description = "当前待审批版本ID")
	private String pendingVersionId;

	@Schema(description = "回款金额")
	private BigDecimal recordAmount;

	@Schema(description = "合计放款金额")
	private BigDecimal totalLoanAmount;

	@Schema(description = "合计成本金额")
	private BigDecimal totalCostAmount;

	@Schema(description = "合计杂费金额")
	private BigDecimal totalMiscFeeAmount;

	@Schema(description = "合计返佣金额")
	private BigDecimal totalCommissionAmount;

	@Schema(description = "合计创收金额")
	private BigDecimal totalRevenueAmount;

	@Schema(description = "回款时间")
	private Long recordEndTime;

	@Schema(description = "最早放款时间")
	private Long firstLoanTime;

	@Schema(description = "最晚放款时间")
	private Long lastLoanTime;

	@Schema(description = "最早回款时间")
	private Long firstRepaymentTime;

	@Schema(description = "最晚回款时间")
	private Long lastRepaymentTime;

	@Schema(description = "乐观锁版本")
	private Integer lockVersion;

	@Schema(description = "组织id")
	private String organizationId;
}
