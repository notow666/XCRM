package cn.cordys.crm.contract.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "contract_version")
public class ContractVersion extends BaseModel {

    @Schema(description = "合同ID")
    private String contractId;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "提交类型")
    private String submitType;

    @Schema(description = "审批状态")
    private String approvalStatus;

    @Schema(description = "修改基于的生效版本ID")
    private String baseEffectiveVersionId;

    @Schema(description = "完整业务值快照")
    private String valueSnapshot;

    @Schema(description = "表单配置快照")
    private String formSnapshot;

    @Schema(description = "字段级差异快照")
    private String changeSnapshot;

    @Schema(description = "审批部门ID快照")
    private String approvalDeptId;

    @Schema(description = "提交人")
    private String submitUser;

    @Schema(description = "提交时间")
    private Long submitTime;

    @Schema(description = "审批人")
    private String approvalUser;

    @Schema(description = "审批时间")
    private Long approvalTime;

    @Schema(description = "审批意见")
    private String approvalOpinion;

    @Schema(description = "组织ID")
    private String organizationId;
}
