package cn.cordys.mmba;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 本对接版本处理的 MMBA 审计/回执行为类型（与文档章节对应）。
 */
public final class MmbaBehaviorTypes {

    private MmbaBehaviorTypes() {
    }

    // 此接口由用户的服务提供，用于接收客户端审计上报的电话接打记录（reqId不为空时，reqId可用于对应API拨打电话接口请求参数中的reqId)
    public static final int CALL_RECORD_AUDIT = 10;
    // 此接口由用户的服务提供，用于接收emm客户端审计上报的通话记录删除信息
    public static final int CALL_RECORD_DELETE_AUDIT = 11;
    //此接口由用户的服务提供，用于接收通过api拨打电话失败的结果（拨打成功的结果通过审计接口获取)
    public static final int DIAL_FAIL_RECEIPT = 13;


    //此接口由用户的服务提供，用于接收emm客户端审计上报的微信聊天记录
    public static final int WX_CHAT_AUDIT = 30;
    //此接口由用户的服务提供，用于接收通过api添加微信好友是否成功的结果
    public static final int ADD_WECHAT_FRIEND = 50;
    //此接口由用户的服务提供，用于接收emm客户端审计上报的微信好友变更记录
    public static final int WX_FRIEND_CHANGE_AUDIT = 60;
    //此接口由用户的服务提供，用于接收emm客户端审计上报的微信好友列表
    public static final int WX_FRIEND_LIST_AUDIT = 61;
    //此接口由用户的服务提供，用于接收emm客户端审计上报的当前设备登陆的微信账号信息
    public static final int WX_ACCOUNT_AUDIT = 100;
    //微信/QQ客户端登录或登出时，产生上报的登录登出数据
    public static final int WX_LOGIN_LOGOUT_AUDIT = 200;

    public static final Map<Integer, String> SUPPORTED = init();

    public static boolean isSupported(int behaviorType) {
        return SUPPORTED.containsKey(behaviorType);
    }

    private static Map<Integer, String> init() {
        Map<Integer, String> i = new HashMap<>(11);
        i.put(CALL_RECORD_AUDIT, "GROUP_BY_AUDIT");
        i.put(CALL_RECORD_DELETE_AUDIT, "GROUP_BY_AUDIT");
        i.put(DIAL_FAIL_RECEIPT, "GROUP_BY_COMMAND");
        i.put(ADD_WECHAT_FRIEND, "GROUP_BY_COMMAND");
        i.put(WX_CHAT_AUDIT, "GROUP_BY_AUDIT");
        i.put(WX_FRIEND_CHANGE_AUDIT, "GROUP_BY_AUDIT");
        i.put(WX_FRIEND_LIST_AUDIT, "GROUP_BY_AUDIT");
        i.put(WX_ACCOUNT_AUDIT, "GROUP_BY_AUDIT");
        i.put(WX_LOGIN_LOGOUT_AUDIT, "GROUP_BY_AUDIT");
        return i;
    }
}
