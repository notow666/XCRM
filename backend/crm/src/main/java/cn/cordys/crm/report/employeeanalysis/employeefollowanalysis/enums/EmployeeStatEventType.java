package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 员工分析事件类型。
 * 用于标记一条统计事件是由哪类具体业务动作产生的。
 */
@Getter
public enum EmployeeStatEventType {
    /** 创建客户 */
    CREATE("CREATE"),
    /** 领取客户 */
    PICK("PICK"),
    /** 分配客户 */
    ASSIGN("ASSIGN"),
    /** 转移客户 */
    TRANSFER("TRANSFER"),
    /** 手工新增跟进 */
    MANUAL_FOLLOW("MANUAL_FOLLOW"),
    /** 电话回调自动补跟进 */
    CALL_AUTO_FOLLOW("CALL_AUTO_FOLLOW"),
    /** 短信回调自动补跟进 */
    SMS_AUTO_FOLLOW("SMS_AUTO_FOLLOW"),
    /** 微信回调自动补跟进 */
    WECHAT_AUTO_FOLLOW("WECHAT_AUTO_FOLLOW"),
    /** 新增微信好友成功 */
    WECHAT_FRIEND_SUCCESS("WECHAT_FRIEND_SUCCESS");

    private final String value;

    EmployeeStatEventType(String value) {
        this.value = value;
    }

    public static EmployeeStatEventType fromValue(String value) {
        for (EmployeeStatEventType type : values()) {
            if (StringUtils.equals(type.value, value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported eventType: " + value);
    }
}
