package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisSummaryItemResponse {

    private String dimensionKey;
    private String dimensionLabel;
    private Integer inboundCustomerCount;
    private Integer contactedCustomerCount;
    private Integer newWechatFriendCount;
    private Integer visitCustomerCount;
    private Integer dialCount;
    private Integer connectedCount;
    private Integer callOver1MinCount;
    private Integer callOver3MinCount;
    private Integer customDurationCallCount;
    private Long callDurationSec;
    private Long avgCallDurationSec;
}
