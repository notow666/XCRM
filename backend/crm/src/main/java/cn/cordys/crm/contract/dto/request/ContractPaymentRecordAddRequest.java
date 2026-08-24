package cn.cordys.crm.contract.dto.request;

import cn.cordys.common.domain.BaseModuleFieldValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author song-cc-rock
 */
@Data
public class ContractPaymentRecordAddRequest {

	@NotBlank
	@Size(max = 255)
	@Schema(description = "回款记录名称", requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Size(max = 32)
	@Schema(description = "负责人（兼容字段，后端固定为合同签约人）")
	private String owner;

	@Schema(description = "回款编号")
	private String no;

	@NotBlank
	@Size(max = 32)
	@Schema(description = "合同ID", requiredMode = Schema.RequiredMode.REQUIRED)
	private String contractId;

	@Size(max = 32)
	@Schema(description = "回款计划ID")
	private String paymentPlanId;

	@Schema(description = "回款金额（兼容字段，后端按产品汇总）")
	private BigDecimal recordAmount;

	@Schema(description = "回款时间")
	private Long recordEndTime;

	@Schema(description = "自定义字段值")
	private List<BaseModuleFieldValue> moduleFields;

	@Valid
	@NotNull
	@Size(min = 1, max = 10)
	@Schema(description = "回款产品明细", requiredMode = Schema.RequiredMode.REQUIRED)
	private List<ContractPaymentRecordProductRequest> products;
}
