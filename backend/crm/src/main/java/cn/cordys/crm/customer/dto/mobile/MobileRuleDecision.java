package cn.cordys.crm.customer.dto.mobile;

import cn.cordys.crm.customer.constants.MobileConflictType;
import cn.cordys.crm.customer.domain.Customer;
import lombok.Builder;
import lombok.Data;

/**
 * 手机号规则判定结果。
 */
@Data
@Builder
public class MobileRuleDecision {

    /**
     * 是否命中冲突。
     */
    private boolean conflict;

    /**
     * 命中的冲突类型。
     */
    private MobileConflictType conflictType;

    /**
     * 命中的冲突客户。
     */
    private Customer conflictCustomer;

    /**
     * 命中的冲突手机号。
     */
    private String conflictMobile;

    /**
     * 构造无冲突结果。
     *
     * @param mobile 当前手机号
     * @return 无冲突判定结果
     */
    public static MobileRuleDecision noConflict(String mobile) {
        return MobileRuleDecision.builder()
                .conflict(false)
                .conflictType(MobileConflictType.NONE)
                .conflictMobile(mobile)
                .build();
    }

    /**
     * 构造冲突结果。
     *
     * @param conflictType 冲突类型
     * @param conflictCustomer 冲突客户
     * @param mobile 当前手机号
     * @return 冲突判定结果
     */
    public static MobileRuleDecision conflict(MobileConflictType conflictType, Customer conflictCustomer, String mobile) {
        return MobileRuleDecision.builder()
                .conflict(true)
                .conflictType(conflictType)
                .conflictCustomer(conflictCustomer)
                .conflictMobile(mobile)
                .build();
    }
}
