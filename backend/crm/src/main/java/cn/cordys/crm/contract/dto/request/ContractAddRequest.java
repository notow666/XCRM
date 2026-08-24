package cn.cordys.crm.contract.dto.request;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ContractAddRequest {

    @NotBlank(message = "{contract.name.required}")
    @Size(max = 255)
    @Schema(description = "合同名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "{contract.customer.required}")
    @Size(max = 32)
    @Schema(description = "客户id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    @Size(max = 32)
    @Schema(description = "负责人（兼容字段，后端以客户负责人为准）")
    private String owner;

    @Schema(description = "累计金额（兼容字段，后端按产品汇总）")
    private String amount;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "签约人", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signerId;

    @Schema(description = "合同开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long startTime;

    @Schema(description = "合同结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long endTime;

    @Schema(description = "自定义字段")
    private List<BaseModuleFieldValue> moduleFields;

    @Schema(description = "表单配置")
    private ModuleFormConfigDTO moduleFormConfigDTO;

    @Valid
    @NotNull
    @Size(min = 1, max = 10)
    @Schema(description = "合同产品信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ContractProductRequest> products;

	@Schema(description = "编号")
	private String number;
}
