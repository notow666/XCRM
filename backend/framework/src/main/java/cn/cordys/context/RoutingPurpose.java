package cn.cordys.context;

/**
 * 租户 JDBC 路由用途：决定请求应访问主库 A 还是随 active 库（USER_REQUEST）。
 */
public enum RoutingPurpose {

    /** 登录用户业务 CRUD、配置读写、列表、导入导出 */
    USER_REQUEST,

    /** 登录鉴权、SSO、组织同步等身份操作 */
    IDENTITY_PRIMARY,

    /** MMBA 回调、线索 push 等外部集成写入 */
    INTEGRATION_PRIMARY,

    /** ApiKey 外部 API 写操作 */
    EXTERNAL_API_PRIMARY,

    /** 数据专员公海导入 */
    DATA_SPECIALIST_PRIMARY,

    /** 定时任务、异步提醒队列等生产维护 */
    PRODUCTION_MAINTAIN,

    /** 影子 active 下禁止的 MMBA 出站等（由 Filter 拦截，不应到达数据源） */
    SHADOW_FORBIDDEN,

    /** 影子库开通/补初始化：强制路由至 B（不看 active_db_role） */
    SHADOW_PROVISION
}
