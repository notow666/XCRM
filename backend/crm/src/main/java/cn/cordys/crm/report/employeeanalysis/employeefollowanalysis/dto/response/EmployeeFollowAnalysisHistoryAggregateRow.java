package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisHistoryAggregateRow {

    private String dimensionKey;
    private String dimensionLabel;
    private String customerId;
    private Integer inboundCustomerFlag;
    private Integer contactedCustomerFlag;
    private Integer newWechatFriendFlag;
    private Integer dialCount;
    private Integer connectedCount;
    private Integer callOver1minCount;
    private Integer callOver3minCount;
    private Long callDurationSec;
}
