package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisMetricRow {

    private String statDate;
    private String organizationId;
    private String customerId;
    private String customerSource;
    private String operatorUserId;
    private String ownerUserName;
    private String ownerDeptId;
    private String ownerDeptName;
    private String bizExtInfo;
    private Integer inboundCustomerFlag;
    private Integer contactedCustomerFlag;
    private Integer newWechatFriendFlag;
    private Integer visitCustomerFlag;
    private Integer dialCount;
    private Integer connectedCount;
    private Integer callOver1minCount;
    private Integer callOver3minCount;
    private Long callDurationSec;
}
