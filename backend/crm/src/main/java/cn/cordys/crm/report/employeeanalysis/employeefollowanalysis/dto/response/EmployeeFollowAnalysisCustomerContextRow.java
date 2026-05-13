package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response;

import lombok.Data;

@Data
public class EmployeeFollowAnalysisCustomerContextRow {

    private String customerId;
    private String customerName;
    private String mobile;
    private String ownerId;
    private String ownerName;
    private String departmentId;
    private String departmentName;
    private String customerSource;
}
