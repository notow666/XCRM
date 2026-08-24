package cn.cordys.crm.report.customerconversion.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Data
@Table(name = "report_customer_conversion_event")
public class CustomerConversionEvent extends BaseModel {

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "合同或回款业务ID")
    private String businessId;

    @Schema(description = "合同ID")
    private String contractId;

    @Schema(description = "回款记录ID")
    private String paymentRecordId;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户名称快照")
    private String customerName;

    @Schema(description = "客户手机号快照")
    private String customerMobile;

    @Schema(description = "客户创建来源快照")
    private String customerSource;

    @Schema(description = "签约人ID快照")
    private String signerId;

    @Schema(description = "签约人姓名快照")
    private String signerName;

    @Schema(description = "签约人部门ID快照")
    private String signerDeptId;

    @Schema(description = "签约人部门名称快照")
    private String signerDeptName;

    @Schema(description = "业务事件时间")
    private Long eventTime;

    @Schema(description = "业务事件日期")
    private LocalDate statDate;

    @Schema(description = "组织ID")
    private String organizationId;
}
