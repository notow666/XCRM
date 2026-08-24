package cn.cordys.crm.contract.constants;


/**
 * 合同状态
 */
public enum ContractStage {


    /**
     * 待签署
     */
    PENDING_SIGNING,

    /**
     * 已签署
     */
    SIGNED,

    /**
     * 兼容旧数据，新合同流程禁止进入该阶段。
     */
    CHANGE,

    /**
     * 履行中
     */
    IN_PROGRESS,

    /**
     * 履行完毕
     */
    COMPLETED_PERFORMANCE,

    /**
     * 作废
     */
    VOID,

    /**
     * 兼容旧数据，新合同流程禁止进入该阶段。
     */
    ARCHIVED,
}
