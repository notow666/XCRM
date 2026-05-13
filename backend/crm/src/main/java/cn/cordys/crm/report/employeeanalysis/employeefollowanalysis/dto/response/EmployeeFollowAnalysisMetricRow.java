package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisMetricRow {

    private String statDate;
    private String customerId;
    private String operatorUserId;
    private Integer inboundCustomerFlag;
    private Integer contactedCustomerFlag;
    private Integer newWechatFriendFlag;
    private Integer dialCount;
    private Integer connectedCount;
    private Integer callOver1minCount;
    private Integer callOver3minCount;
    private Long callDurationSec;
}
