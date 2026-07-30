package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.constants.MobileConflictType;
import cn.cordys.crm.customer.constants.MobileRuleScene;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.mobile.MobileRuleContext;
import cn.cordys.crm.customer.dto.mobile.MobileRuleDecision;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 手机号规则判定引擎。
 * 负责统一解释配置、来源和场景，并输出稳定的判定结果。
 */
@Service
public class CustomerMobileRuleEngine {

    @Resource
    private CustomerMobileConflictQueryService customerMobileConflictQueryService;

    /**
     * 统一执行手机号规则判定。
     *
     * @param context 判定上下文
     * @return 判定结果
     */
    public MobileRuleDecision evaluate(MobileRuleContext context) {
        String normalizedMobile = StringUtils.trimToNull(context.getMobile());
        if (normalizedMobile == null) {
            return MobileRuleDecision.noConflict(null);
        }
        return switch (context.getScene()) {
            case SAVE, FIELD_REPEAT_CHECK -> evaluateSaveOrFieldCheck(context, normalizedMobile);
            case OWNER_RECEIVE -> evaluateOwnerReceive(context, normalizedMobile);
        };
    }

    /**
     * 判断负责人接收客户时是否会与现有名下客户冲突。
     *
     * @param mobile 手机号
     * @param ownerCustomers 负责人当前名下客户快照
     * @param incomingCreateSource 转入客户来源
     * @return 是否冲突
     */
    public boolean hasOwnerReceiveConflict(String mobile, List<Customer> ownerCustomers, String incomingCreateSource) {
        return evaluateOwnerReceive(mobile, ownerCustomers).isConflict();
    }

    /**
     * 保存和字段查重场景使用同一套判定逻辑。
     *
     * @param context 判定上下文
     * @param mobile 归一化后的手机号
     * @return 判定结果
     */
    private MobileRuleDecision evaluateSaveOrFieldCheck(MobileRuleContext context, String mobile) {
        Customer ownerConflict = customerMobileConflictQueryService.findOwnerOwnedConflict(
                context.getCustomerId(), mobile, context.getOwnerId(), context.getOrgId());
        if (ownerConflict != null) {
            return MobileRuleDecision.conflict(MobileConflictType.OWNER_PRIVATE_CONFLICT, ownerConflict, mobile);
        }
        if (CustomerCreateSource.isPoolSource(context.getCreateSource())) {
            Customer poolImportConflict = customerMobileConflictQueryService.findPoolSourceConflict(
                    context.getCustomerId(), mobile, context.getOrgId());
            if (poolImportConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.POOL_IMPORT_CONFLICT, poolImportConflict, mobile);
            }
        }
        return MobileRuleDecision.noConflict(mobile);
    }

    /**
     * 负责人接收客户场景。
     * 同一负责人名下全部来源客户永久执行手机号唯一规则。
     *
     * @param context 判定上下文
     * @param mobile 归一化后的手机号
     * @return 判定结果
     */
    private MobileRuleDecision evaluateOwnerReceive(MobileRuleContext context, String mobile) {
        List<Customer> ownerCustomers = customerMobileConflictQueryService.listOwnerOwnedCustomers(
                context.getOwnerId(), context.getOrgId(), context.getCustomerId());
        MobileRuleDecision ownerDecision = evaluateOwnerReceive(mobile, ownerCustomers);
        if (ownerDecision.isConflict() || !CustomerCreateSource.isPoolSource(context.getCreateSource())) {
            return ownerDecision;
        }
        Customer poolSourceConflict = customerMobileConflictQueryService.findPoolSourceConflict(
                context.getCustomerId(), mobile, context.getOrgId());
        if (poolSourceConflict != null) {
            return MobileRuleDecision.conflict(MobileConflictType.POOL_IMPORT_CONFLICT, poolSourceConflict, mobile);
        }
        return MobileRuleDecision.noConflict(mobile);
    }

    /**
     * 基于负责人客户快照执行接收冲突判断。
     *
     * @param mobile 归一化后的手机号
     * @param ownerCustomers 负责人客户快照
     * @return 判定结果
     */
    private MobileRuleDecision evaluateOwnerReceive(String mobile, List<Customer> ownerCustomers) {
        if (ownerCustomers == null || ownerCustomers.isEmpty()) {
            return MobileRuleDecision.noConflict(mobile);
        }
        Customer ownerConflict = ownerCustomers.stream()
                .filter(customer -> StringUtils.equals(mobile, StringUtils.trimToNull(customer.getMobile())))
                .findFirst()
                .orElse(null);
        if (ownerConflict != null) {
            return MobileRuleDecision.conflict(MobileConflictType.OWNER_PRIVATE_CONFLICT, ownerConflict, mobile);
        }
        return MobileRuleDecision.noConflict(mobile);
    }
}
