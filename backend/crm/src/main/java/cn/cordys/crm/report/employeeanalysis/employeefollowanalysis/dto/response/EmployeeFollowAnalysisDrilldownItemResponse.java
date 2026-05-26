package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisDrilldownItemResponse {

    private String auditId;
    private String customerId;
    private String customerName;
    private String mobile;
    private String ownerName;
    private String departmentName;
    private String customerSource;
    private Long eventTime;
    private String friendPhone;
    private String contactImAppNickName;
    private String contactImAppAccount;
    private String contactImAppNote;
    private String staffName;
    private String bizExtInfo;
    private String beginTime;
    private String endTime;
    private Integer duration;
    private Integer isConnected;
    private Integer direction;
}
