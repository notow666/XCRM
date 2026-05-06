package cn.cordys.mmba;

/**
 * MMBA 接口路径常量。
 * 这里只放相对路径，调用时由网关层再拼接 apiBaseUrl。
 */
public interface MmbaApiPaths {

    /**
     * 获取 accessToken。
     */
    String ACCESS_TOKEN = "/mmba/api/user/v3/accessToken";

    /**
     * 拨打电话。
     */
    String PHONE_DIAL = "/mmba/api/phone/v3/dial";

    /**
     * 限制电话拨打。
     * 已按原始接口文档《限制电话拨打.docx》确认。
     */
    String PHONE_CALL_LIMIT = "/mmba/api/phone/v3/callLimit";

    /**
     * 发送短信。
     */
    String PHONE_SEND_MSG = "/mmba/api/phone/v3/sendMsg";

    /**
     * 发送微信消息。
     */
    String IM_SEND_WX_MSG = "/mmba/api/im/v3/sendWxMsg";

    /**
     * 添加微信好友。
     */
    String IM_ADD_FRIEND = "/mmba/api/im/v3/addFriend";

    /**
     * 发送朋友圈。
     * 已按原始接口文档《发送朋友圈.docx》确认。
     */
    String IM_SEND_WX_MOMENT = "/mmba/api/im/v3/sendWXMoment";

    /**
     * 修改微信好友备注或描述。
     */
    String IM_MODIFY_WX_FRIEND_REMARK = "/mmba/api/im/v3/editFriend";

    /**
     * 查询已登录微信账号。
     */
    String IM_QUERY_LOGIN_WX_ACCOUNT = "/mmba/api/im/v3/queryWechatAccount";

    /**
     * 查询微信好友列表。
     */
    String IM_QUERY_WX_FRIEND_LIST = "/mmba/api/im/v3/queryWechatFriends";

    /**
     * 查询重复微信好友。
     */
    String IM_QUERY_DUPLICATE_WX_FRIEND = "/mmba/api/im/v3/queryDuplicateWxFriend";

    /**
     * 查询微信聊天记录。
     */
    String IM_QUERY_CHAT_MESSAGE = "/mmba/api/im/v3/queryChatMessage";

    String DEVICE_LIST_QUERY = "/mmba/api/phone/v3/deviceList";

    /**
     * 设备消息推送。
     * 已按原始接口文档《设备消息推送.docx》确认。
     */
    String DEVICE_MESSAGE_PUSH = "/mmba/api/device/message/v3/send";

    /**
     * 下载带鉴权的 MMBA 文件。
     */
    String FILE_FETCH_FILE = "/mmba/api/file/v3/fetchFile";

    /**
     * 下载带鉴权的媒体资源。
     */
    String FILE_FETCH_ASSET = "/mmba/api/file/v3/fetchAsset";
}
