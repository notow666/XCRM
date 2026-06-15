package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "report_employee_stat_day")
public class EmployeeStatDay {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "统计日期")
    private String statDate;

    @Schema(description = "客户负责人ID")
    private String ownerUserId;

    @Schema(description = "客户负责人姓名快照")
    private String ownerUserName;

    @Schema(description = "客户负责人部门ID快照")
    private String ownerDeptId;

    @Schema(description = "客户负责人部门名称快照")
    private String ownerDeptName;

    @Schema(description = "入库客户数")
    private Integer inboundCustomerCount;

    @Schema(description = "联系客户数")
    private Integer contactedCustomerCount;

    @Schema(description = "拨打数量")
    private Integer dialCount;

    @Schema(description = "拨打接通数量")
    private Integer connectedCount;

    @Schema(description = "通话时长秒")
    private Long callDurationSec;

    @Schema(description = "一分钟以上通话数")
    @Column(name = "call_over_1min_count")
    private Integer callOver1minCount;

    @Schema(description = "三分钟以上通话数")
    @Column(name = "call_over_3min_count")
    private Integer callOver3minCount;

    @Schema(description = "新增微信好友数量")
    private Integer newWechatFriendCount;

    @Schema(description = "上门客户数")
    private Integer visitCustomerCount;

    @Schema(description = "创建人")
    private String createUser;

    @Schema(description = "创建时间")
    private Long createTime;
}
