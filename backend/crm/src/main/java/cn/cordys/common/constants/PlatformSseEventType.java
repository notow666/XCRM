package cn.cordys.common.constants;

/**
 * 平台级 SSE 广播事件类型（管理中心 → 租户侧 / 数据专员在线用户）。
 */
public final class PlatformSseEventType {

    public static final String PLATFORM_SYSTEM_ANNOUNCEMENT = "PLATFORM_SYSTEM_ANNOUNCEMENT";
    public static final String PLATFORM_FORCE_LOGOUT = "PLATFORM_FORCE_LOGOUT";
    /** 强制全员下线执行完成（仅推送给管理中心平台管理员 SSE） */
    public static final String PLATFORM_FORCE_LOGOUT_DONE = "PLATFORM_FORCE_LOGOUT_DONE";

    /**
     * Redis 发布订阅中标识平台广播（非租户内单用户通知）。
     */
    public static final String BROADCAST_SCOPE_TENANT_AND_DS = "TENANT_AND_DS";
    /** 仅推送给 PLATFORM SSE 连接（管理中心） */
    public static final String BROADCAST_SCOPE_PLATFORM = "PLATFORM";

    private PlatformSseEventType() {
    }
}
