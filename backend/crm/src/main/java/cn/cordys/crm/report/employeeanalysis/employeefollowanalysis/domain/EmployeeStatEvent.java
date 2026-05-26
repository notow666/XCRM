package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "report_employee_stat_event")
public class EmployeeStatEvent {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "事件发生时间")
    private Long eventTime;

    @Schema(description = "事件发生日期")
    private String statDate;

    @Schema(description = "指标类型")
    private String metricType;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "客户姓名快照")
    private String customerName;

    @Schema(description = "客户手机号快照")
    private String customerMobile;

    @Schema(description = "客户来源快照")
    private String customerSource;

    @Schema(description = "客户负责人ID")
    private String ownerUserId;

    @Schema(description = "客户负责人姓名快照")
    private String ownerUserName;

    @Schema(description = "客户负责人部门ID快照")
    private String ownerDeptId;

    @Schema(description = "客户负责人部门名称快照")
    private String ownerDeptName;

    @Schema(description = "操作人ID")
    private String operatorUserId;

    @Schema(description = "操作人姓名快照")
    private String operatorUserName;

    @Schema(description = "操作人部门ID快照")
    private String operatorDeptId;

    @Schema(description = "操作人部门名称快照")
    private String operatorDeptName;

    @Schema(description = "业务链路标识")
    private String bizTraceId;

    @Schema(description = "来源表名")
    private String sourceTableName;

    @Schema(description = "有效标识 0待确认 1已确认")
    private Integer validFlag;

    @Schema(description = "创建人")
    private String createUser;

    @Schema(description = "创建时间")
    private Long createTime;
}
