package cn.cordys.common.constants;

import java.util.Map;

/**
 * 联动场景
 *
 * @author song-cc-rock
 */
public enum LinkScenarioKey {

    /**
     * 线索转客户
     */
    CLUE_TO_CUSTOMER,
	/**
	 * 线索转联系人
	 */
	CLUE_TO_CONTACT,
    /**
     * 线索转商机
     */
    CLUE_TO_OPPORTUNITY,
    /**
     * 客户转商机
     */
    CUSTOMER_TO_OPPORTUNITY,
    /**
     * 线索转记录
     */
    CLUE_TO_RECORD,
    /**
     * 客户转记录
     */
    CUSTOMER_TO_RECORD,
    /**
     * 商机转记录
     */
    OPPORTUNITY_TO_RECORD,
    /**
     * 计划转记录
     */
    PLAN_TO_RECORD,
    /**
     * 合同转发票
     */
    CONTRACT_TO_INVOICE,
    /**
     * 合同转订单
     */
    CONTRACT_TO_ORDER;

    public static Map<String, String> clue2Customer() {
        return Map.of(
                "customerName", "clueName",
                "customerMobile", "clueContactPhone",
                "customerSource", "clueSource",
                "customerLevel", "clueLevel",
                "customerTag", "clueTag",
                "customerCompany", "clueCompany",
                "customerArea", "clueArea",
                "customerOwner", "clueOwner"
        );
    }
}
