package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.constants.MobileConflictType;
import cn.cordys.crm.customer.constants.MobileRuleScene;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerRepeatRuleConfig;
import cn.cordys.crm.customer.dto.mobile.MobileRuleContext;
import cn.cordys.crm.customer.dto.mobile.MobileRuleDecision;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 手机号规则判定引擎。
 * 负责统一解释配置、来源和场景，并输出稳定的判定结果。
 */
@Service
public class CustomerMobileRuleEngine {

    /**
     * 受私海重复规则约束的客户来源。
     */
    private static final List<String> PRIVATE_REPEAT_SOURCES = Arrays.asList(
            CustomerCreateSource.MANUAL_CREATE,
            CustomerCreateSource.PRIVATE_IMPORT
    );

    @Resource
    private CustomerMobileConflictQueryService customerMobileConflictQueryService;
    @Resource
    private CustomerRepeatRuleConfigService customerRepeatRuleConfigService;

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
     * 判断某个来源是否使用私海重复规则。
     *
     * @param createSource 客户来源
     * @return 是否使用私海重复规则
     */
    public boolean usesPrivateRepeatRule(String createSource) {
        return PRIVATE_REPEAT_SOURCES.contains(createSource);
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
        return evaluateOwnerReceive(mobile, ownerCustomers, incomingCreateSource).isConflict();
    }

    /**
     * 获取私海重复规则来源列表。
     *
     * @return 私海重复规则来源列表
     */
    public List<String> getPrivateRepeatSources() {
        return PRIVATE_REPEAT_SOURCES;
    }

    /**
     * 获取当前重复规则启用时的时间阈值。
     *
     * @return 时间阈值；未启用时返回 null
     */
    public Long getRepeatRuleCutoffTime() {
        CustomerRepeatRuleConfig config = customerRepeatRuleConfigService.getConfigEntity();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        return System.currentTimeMillis() - config.getRepeatAfterDays() * 24L * 60L * 60L * 1000L;
    }

    /**
     * 保存和字段查重场景使用同一套判定逻辑。
     *
     * @param context 判定上下文
     * @param mobile 归一化后的手机号
     * @return 判定结果
     */
    private MobileRuleDecision evaluateSaveOrFieldCheck(MobileRuleContext context, String mobile) {
        if (usesPrivateRepeatRule(context.getCreateSource())) {
            Customer privateConflict = customerMobileConflictQueryService.findPrivateSourceConflict(
                    context.getCustomerId(), mobile, getPrivateRepeatSources(), getRepeatRuleCutoffTime());
            if (privateConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.PRIVATE_SOURCE_CONFLICT, privateConflict, mobile);
            }
            Customer ownerPoolConflict = customerMobileConflictQueryService.findOwnerPoolConflict(
                    context.getCustomerId(), mobile, context.getOwnerId(), context.getOrgId());
            if (ownerPoolConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.OWNER_POOL_CONFLICT, ownerPoolConflict, mobile);
            }
            return MobileRuleDecision.noConflict(mobile);
        }
        if (StringUtils.equals(context.getCreateSource(), CustomerCreateSource.POOL_IMPORT)) {
            Customer poolImportConflict = customerMobileConflictQueryService.findPoolImportConflict(
                    context.getCustomerId(), mobile, context.getOrgId());
            if (poolImportConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.POOL_IMPORT_CONFLICT, poolImportConflict, mobile);
            }
            Customer ownerPrivateConflict = customerMobileConflictQueryService.findOwnerPrivateConflict(
                    context.getCustomerId(), mobile, context.getOwnerId(), context.getOrgId(), getPrivateRepeatSources());
            if (ownerPrivateConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.OWNER_PRIVATE_CONFLICT, ownerPrivateConflict, mobile);
            }
            return MobileRuleDecision.noConflict(mobile);
        }
        return MobileRuleDecision.noConflict(mobile);
    }

    /**
     * 负责人接收客户场景。
     * 规则按“转入客户来源”区分：
     * 1. 转入公海导入客户时，负责人名下已有手工创建或公海导入同手机号客户都要拦截。
     * 2. 转入手工创建客户时，负责人名下已有公海导入同手机号客户要拦截；
     *    对负责人名下已有手工创建同手机号客户，仍按重复规则时间窗判断是否允许重复。
     *
     * @param context 判定上下文
     * @param mobile 归一化后的手机号
     * @return 判定结果
     */
    private MobileRuleDecision evaluateOwnerReceive(MobileRuleContext context, String mobile) {
        List<Customer> ownerCustomers = customerMobileConflictQueryService.listOwnerOwnedCustomers(
                context.getOwnerId(), context.getOrgId(), context.getCustomerId());
        return evaluateOwnerReceive(mobile, ownerCustomers, context.getCreateSource());
    }

    /**
     * 基于负责人客户快照执行接收冲突判断。
     *
     * @param mobile 归一化后的手机号
     * @param ownerCustomers 负责人客户快照
     * @param incomingCreateSource 转入客户来源
     * @return 判定结果
     */
    private MobileRuleDecision evaluateOwnerReceive(String mobile, List<Customer> ownerCustomers, String incomingCreateSource) {
        if (ownerCustomers == null || ownerCustomers.isEmpty()) {
            return MobileRuleDecision.noConflict(mobile);
        }
        Customer ownerPoolConflict = ownerCustomers.stream()
                .filter(customer -> StringUtils.equals(mobile, StringUtils.trimToNull(customer.getMobile())))
                .filter(customer -> StringUtils.equals(customer.getCreateSource(), CustomerCreateSource.POOL_IMPORT))
                .findFirst()
                .orElse(null);
        if (ownerPoolConflict != null) {
            return MobileRuleDecision.conflict(MobileConflictType.OWNER_POOL_CONFLICT, ownerPoolConflict, mobile);
        }
        if (StringUtils.equals(incomingCreateSource, CustomerCreateSource.POOL_IMPORT)) {
            Customer ownerPrivateConflict = ownerCustomers.stream()
                    .filter(customer -> StringUtils.equals(mobile, StringUtils.trimToNull(customer.getMobile())))
                    .filter(customer -> usesPrivateRepeatRule(customer.getCreateSource()))
                    .findFirst()
                    .orElse(null);
            if (ownerPrivateConflict != null) {
                return MobileRuleDecision.conflict(MobileConflictType.OWNER_PRIVATE_CONFLICT, ownerPrivateConflict, mobile);
            }
            return MobileRuleDecision.noConflict(mobile);
        }
        Long cutoffTime = getRepeatRuleCutoffTime();
        Customer ownerPrivateConflict = ownerCustomers.stream()
                .filter(customer -> StringUtils.equals(mobile, StringUtils.trimToNull(customer.getMobile())))
                .filter(customer -> usesPrivateRepeatRule(customer.getCreateSource()))
                .filter(customer -> cutoffTime == null
                        || customer.getCreateTime() == null
                        || customer.getCreateTime() > cutoffTime)
                .findFirst()
                .orElse(null);
        if (ownerPrivateConflict != null) {
            return MobileRuleDecision.conflict(MobileConflictType.OWNER_PRIVATE_CONFLICT, ownerPrivateConflict, mobile);
        }
        return MobileRuleDecision.noConflict(mobile);
    }
}
