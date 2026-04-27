package cn.cordys.mmba;

/**
 * MMBA 接口路径（明文 JSON）
 */
public interface MmbaApiPaths {
    String ACCESS_TOKEN = "/mmba/api/user/v3/accessToken";

    String PHONE_DIAL = "/mmba/api/phone/v3/dial";
    String PHONE_SEND_MSG = "/mmba/api/phone/v3/sendMsg";

    String IM_SEND_WX_MSG = "/mmba/api/im/v3/sendWxMsg";
    String IM_ADD_FRIEND = "/mmba/api/im/v3/addFriend";

    String IM_MODIFY_WX_FRIEND_REMARK = "/mmba/api/im/v3/editFriend";

    String IM_QUERY_LOGIN_WX_ACCOUNT = "/mmba/api/im/v3/queryLoginWxAccount";
    String IM_QUERY_WX_FRIEND_LIST = "/mmba/api/im/v3/queryWxFriendList";
    String IM_QUERY_DUPLICATE_WX_FRIEND = "/mmba/api/im/v3/queryDuplicateWxFriend";
    String IM_QUERY_CHAT_MESSAGE = "/mmba/api/im/v3/queryChatMessage";

    /** 文档 8.1：带鉴权下载 MMBA 文件 */
    String FILE_FETCH_FILE = "/mmba/api/file/v3/fetchFile";
    /** 文档 8.2：带鉴权下载媒体资源 */
    String FILE_FETCH_ASSET = "/mmba/api/file/v3/fetchAsset";
}
