package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 员工分析指标类型。
 * 用于标记一条统计事件归属于哪个统计指标大类。
 */
@Getter
public enum EmployeeStatMetricType {
    /** 入库动作 */
    INBOUND("INBOUND"),
    /** 联系动作 */
    CONTACT("CONTACT"),
    /** 新增微信好友动作 */
    WECHAT_FRIEND("WECHAT_FRIEND"),
    /** 上门客户动作 */
    VISIT_CUSTOMER("VISIT_CUSTOMER");

    private final String value;

    EmployeeStatMetricType(String value) {
        this.value = value;
    }

    public static EmployeeStatMetricType fromValue(String value) {
        for (EmployeeStatMetricType type : values()) {
            if (StringUtils.equals(type.value, value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported metricType: " + value);
    }
}
