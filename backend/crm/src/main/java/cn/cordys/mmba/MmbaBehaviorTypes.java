package cn.cordys.mmba;

import java.util.HashMap;
import java.util.Map;

/**
 * MMBA 回调 behaviorType 常量。
 * 这里同时维护当前系统支持的类型，以及每个类型应该进入审计分组还是结果分组。
 */
public final class MmbaBehaviorTypes {

    private MmbaBehaviorTypes() {
    }

    /**
     * 通话记录审计。
     */
    public static final int CALL_RECORD_AUDIT = 10;

    /**
     * 通话记录删除审计。
     * 当前项目不单独实现业务处理，但保留常量便于后续扩展。
     */
    public static final int CALL_RECORD_DELETE_AUDIT = 11;

    /**
     * 拨打电话失败回执。
     */
    public static final int DIAL_FAIL_RECEIPT = 13;

    /**
     * 短/彩信记录审计。
     */
    public static final int SMS_RECORD_AUDIT = 20;

    /**
     * 短信发送失败回执。
     */
    public static final int SMS_FAIL_RECEIPT = 22;

    /**
     * 微信聊天审计。
     */
    public static final int WX_CHAT_AUDIT = 30;

    /**
     * 发送微信消息回执。
     */
    public static final int WX_MESSAGE_RECEIPT = 31;

    /**
     * 添加微信好友回执。
     */
    public static final int ADD_WECHAT_FRIEND_RECEIPT = 50;

    /**
     * 微信好友变更审计。
     */
    public static final int WX_FRIEND_CHANGE_AUDIT = 60;

    /**
     * 微信好友列表审计。
     */
    public static final int WX_FRIEND_LIST_AUDIT = 61;

    /**
     * 发送朋友圈回执。
     */
    public static final int WX_MOMENT_RECEIPT = 81;

    /**
     * 修改好友备注/描述回执。
     */
    public static final int WX_REMARK_RECEIPT = 91;

    /**
     * 微信账号审计。
     */
    public static final int WX_ACCOUNT_AUDIT = 100;

    /**
     * 设备信息审计。
     */
    public static final int DEVICE_INFO_AUDIT = 120;

    /**
     * 微信登录登出审计。
     */
    public static final int WX_LOGIN_LOGOUT_AUDIT = 200;

    /**
     * 当前系统已支持的 behaviorType -> 分组 映射。
     */
    public static final Map<Integer, String> SUPPORTED = init();

    /**
     * 判断当前 behaviorType 是否已在系统中注册。
     */
    public static boolean isSupported(int behaviorType) {
        return SUPPORTED.containsKey(behaviorType);
    }

    /**
     * 初始化回调类型分组。
     * 审计类进入 audit consumer 处理链，结果类进入 command consumer 处理链。
     */
    private static Map<Integer, String> init() {
        Map<Integer, String> groups = new HashMap<>(16);
        groups.put(CALL_RECORD_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(DIAL_FAIL_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(SMS_RECORD_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(SMS_FAIL_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(WX_CHAT_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(WX_MESSAGE_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(ADD_WECHAT_FRIEND_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(WX_FRIEND_CHANGE_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(WX_FRIEND_LIST_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(WX_MOMENT_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(WX_REMARK_RECEIPT, MmbaConstants.GROUP_BY_COMMAND);
        groups.put(WX_ACCOUNT_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(DEVICE_INFO_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        groups.put(WX_LOGIN_LOGOUT_AUDIT, MmbaConstants.GROUP_BY_AUDIT);
        return groups;
    }
}
