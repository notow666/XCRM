package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums;

import org.apache.commons.lang3.StringUtils;

public enum EmployeeFollowAnalysisDimensionType {
    EMPLOYEE_NAME("employeeName"),
    EMPLOYEE_DEPT("employeeDept"),
    CUSTOMER_SOURCE("customerSource"),
    STAT_DAY("statDay"),
    STAT_MONTH("statMonth");

    private final String value;

    EmployeeFollowAnalysisDimensionType(String value) {
        this.value = value;
    }

    public static EmployeeFollowAnalysisDimensionType fromValue(String value) {
        for (EmployeeFollowAnalysisDimensionType type : values()) {
            if (StringUtils.equals(type.value, value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported dimensionType: " + value);
    }
}
