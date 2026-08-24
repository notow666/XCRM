package cn.cordys.crm.contract.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Table(name = "contract")
public class Contract extends BaseModel {

    @Schema(description = "合同名称")
    private String name;

    @Schema(description = "客户id")
    private String customerId;

    @Schema(description = "客户名称快照")
    private String customerNameSnapshot;

    @Schema(description = "客户手机号快照")
    private String customerMobileSnapshot;

    @Schema(description = "客户创建来源快照")
    private String customerSourceSnapshot;

    @Schema(description = "合同负责人")
    private String owner;

    @Schema(description = "合同负责人姓名快照")
    private String ownerNameSnapshot;

    @Schema(description = "签约人ID")
    private String signerId;

    @Schema(description = "签约人姓名快照")
    private String signerNameSnapshot;

    @Schema(description = "签约人部门ID快照")
    private String signerDeptIdSnapshot;

    @Schema(description = "签约人部门名称快照")
    private String signerDeptNameSnapshot;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "合计应回款金额")
    private BigDecimal expectedRepaymentAmount;

    @Schema(description = "编号")
    private String number;

    @Schema(description = "审核状态")
    private String approvalStatus;

    @Schema(description = "当前生效版本ID")
    private String effectiveVersionId;

    @Schema(description = "当前待审批版本ID")
    private String pendingVersionId;

    @Schema(description = "乐观锁版本")
    private Integer lockVersion;

    @Schema(description = "合同阶段")
    private String stage;

    @Schema(description = "合同开始时间")
    private Long startTime;

    @Schema(description = "合同结束时间")
    private Long endTime;

    @Schema(description = "作废原因")
    private String voidReason;

    @Schema(description = "组织id")
    private String organizationId;
}
