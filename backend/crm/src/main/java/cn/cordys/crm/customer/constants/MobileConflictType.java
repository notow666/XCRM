package cn.cordys.crm.customer.constants;

/**
 * 手机号规则命中的冲突类型。
 */
public enum MobileConflictType {

    /**
     * 未命中任何冲突。
     */
    NONE,

    /**
     * 命中私海来源客户冲突。
     * 对应 MANUAL_CREATE、PRIVATE_IMPORT 的重复规则。
     */
    PRIVATE_SOURCE_CONFLICT,

    /**
     * 命中当前负责人名下、公海导入来源客户冲突。
     */
    OWNER_POOL_CONFLICT,

    /**
     * 命中当前负责人名下、私海来源客户冲突。
     * 该冲突同样受“X天后允许重复”配置影响。
     */
    OWNER_PRIVATE_CONFLICT,

    /**
     * 命中公海导入来源冲突。
     * 用于 POOL_IMPORT 客户保存或查重时，校验系统内其他公海导入来源客户是否已使用该手机号。
     */
    POOL_IMPORT_CONFLICT
}
