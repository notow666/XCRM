package cn.cordys.security;

/**
 * 在线 Session 注册表：登录/登出时维护 Redis 索引，供平台概览与强制下线使用。
 */
public interface OnlineSessionRegistry {

    void onSessionUserBound(SessionUser sessionUser, String sessionId);

    void onSessionEnded(String sessionId, SessionUser sessionUser);

    /**
     * 删除指定 principal 的全部 Session。
     *
     * @return 实际删除的 Session 数量
     */
    int kickPrincipal(String principal);

    OnlineSessionStats snapshot();

    /**
     * 用全量扫描结果校准注册表（低频任务调用）。
     */
    void reconcile(OnlineSessionStats scanned);

    /**
     * 从 Redis 已有 Session / principal 索引补写注册表（启动或 reconcile 时调用）。
     */
    void warmUpFromExistingSessions();
}
