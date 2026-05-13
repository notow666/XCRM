package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "report_employee_follow_analysis_fact_day")
public class EmployeeFollowAnalysisFactDay extends BaseModel {

    private String statDate;
    private String customerId;
    private String operatorUserId;
    private Integer inboundCustomerFlag;
    private Integer contactedCustomerFlag;
    private Integer newWechatFriendFlag;
    private Integer dialCount;
    private Integer connectedCount;
    // 字段名里带数字时，框架的自动下划线转换不会补出 1 前面的下划线，这里显式绑定到事实表列名。
    @Column(name = "call_over_1min_count")
    private Integer callOver1minCount;
    @Column(name = "call_over_3min_count")
    private Integer callOver3minCount;
    private Long callDurationSec;
}
