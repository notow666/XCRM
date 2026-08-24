package cn.cordys.crm.contract.constants;


/**
 * 合同审核状态
 */
public enum ContractApprovalStatus {

    /**
     * 审核中
     */
    APPROVING,

    /**
     * 通过
     */
    APPROVED,

    /**
     * 不通过
     */
    UNAPPROVED,

    /**
     * 兼容发票、报价和旧数据，新合同流程禁止使用。
     */
    REVOKED,

    /**
     * 兼容关闭审批的旧模块，新合同流程禁止使用。
     */
    NONE,
}
