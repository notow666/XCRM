package cn.cordys.crm.customer.service;

/**
 * 客户删除场景。自动删除场景会在短事务内重新校验候选条件。
 */
public enum CustomerDeleteScene {
    MANUAL,
    POOL_AUTO,
    PRIVATE_AUTO
}
