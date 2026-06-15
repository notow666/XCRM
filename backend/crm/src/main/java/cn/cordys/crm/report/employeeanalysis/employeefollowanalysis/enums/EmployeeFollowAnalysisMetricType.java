package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums;

import org.apache.commons.lang3.StringUtils;

public enum EmployeeFollowAnalysisMetricType {
    INBOUND_CUSTOMER("inboundCustomer"),
    CONTACTED_CUSTOMER("contactedCustomer"),
    NEW_WECHAT_FRIEND("newWechatFriends"),
    VISIT_CUSTOMER("visitCustomer"),
    DIAL_COUNT("dialCount"),
    CONNECTED_COUNT("connectedCount"),
    CALL_OVER_1MIN("callOver1Min"),
    CALL_OVER_3MIN("callOver3Min");

    private final String value;

    EmployeeFollowAnalysisMetricType(String value) {
        this.value = value;
    }

    public static EmployeeFollowAnalysisMetricType fromValue(String value) {
        for (EmployeeFollowAnalysisMetricType type : values()) {
            if (StringUtils.equals(type.value, value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported metricType: " + value);
    }
}
