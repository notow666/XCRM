package cn.cordys.mmba;

/**
 * MMBA 请求业务类型常量。
 * 这些值写入 mmba_request_record.biz_type，用来标识 CRM 主动发起的调用属于哪一类业务。
 */
public final class MmbaBizTypes {

    private MmbaBizTypes() {
    }

    /**
     * 拨打电话。
     */
    public static final String CALL_DIAL = "CALL_DIAL";

    /**
     * 限制电话拨打。
     */
    public static final String CALL_LIMIT = "CALL_LIMIT";

    /** 清除通话记录。 */
    public static final String CALL_LOG_CLEAN = "CALL_LOG_CLEAN";

    /**
     * 发送短信。
     */
    public static final String SMS_SEND = "SMS_SEND";

    /**
     * 发送微信消息。
     */
    public static final String WX_MSG_SEND = "WX_MSG_SEND";

    /**
     * 添加微信好友。
     */
    public static final String WX_FRIEND_ADD = "WX_FRIEND_ADD";

    /**
     * 发送朋友圈。
     */
    public static final String WX_SEND_MOMENT = "WX_SEND_MOMENT";

    /**
     * 修改微信好友备注或描述。
     */
    public static final String WX_FRIEND_REMARK_MODIFY = "WX_FRIEND_REMARK_MODIFY";

    /**
     * 查询已登录微信账号。
     */
    public static final String WX_LOGIN_ACCOUNT_QUERY = "WX_LOGIN_ACCOUNT_QUERY";

    /**
     * 查询微信好友列表。
     */
    public static final String WX_FRIEND_LIST_QUERY = "WX_FRIEND_LIST_QUERY";

    /**
     * 查询重复微信好友。
     */
    public static final String WX_DUPLICATE_FRIEND_QUERY = "WX_DUPLICATE_FRIEND_QUERY";

    /**
     * 查询微信聊天记录。
     */
    public static final String WX_CHAT_QUERY = "WX_CHAT_QUERY";

    public static final String DEVICE_LIST_QUERY = "DEVICE_LIST_QUERY";

    /**
     * 设备消息推送。
     */
    public static final String DEVICE_PUSH = "DEVICE_PUSH";

    /**
     * 下载带鉴权的普通文件。
     */
    public static final String FILE_FETCH = "FILE_FETCH";

    /**
     * 下载带鉴权的媒体资源。
     */
    public static final String ASSET_FETCH = "ASSET_FETCH";
}
