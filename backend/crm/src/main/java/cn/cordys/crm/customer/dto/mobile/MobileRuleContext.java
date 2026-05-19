package cn.cordys.crm.customer.dto.mobile;

import cn.cordys.crm.customer.constants.MobileRuleScene;
import lombok.Builder;
import lombok.Data;

/**
 * 手机号规则判定上下文。
 */
@Data
@Builder
public class MobileRuleContext {

    /**
     * 当前待判定的手机号。
     */
    private String mobile;

    /**
     * 当前客户ID，编辑场景用于排除自身。
     */
    private String customerId;

    /**
     * 当前负责人ID。
     */
    private String ownerId;

    /**
     * 当前组织ID。
     */
    private String orgId;

    /**
     * 当前客户创建来源。
     */
    private String createSource;

    /**
     * 当前判定发生的业务场景。
     */
    private MobileRuleScene scene;
}
