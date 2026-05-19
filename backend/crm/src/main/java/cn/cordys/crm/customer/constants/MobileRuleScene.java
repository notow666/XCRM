package cn.cordys.crm.customer.constants;

/**
 * 手机号规则判定所处的业务场景。
 */
public enum MobileRuleScene {

    /**
     * 客户新增、编辑保存场景。
     */
    SAVE,

    /**
     * 表单字段实时查重场景。
     */
    FIELD_REPEAT_CHECK,

    /**
     * 负责人领取、分配、转移接收客户场景。
     */
    OWNER_RECEIVE
}
