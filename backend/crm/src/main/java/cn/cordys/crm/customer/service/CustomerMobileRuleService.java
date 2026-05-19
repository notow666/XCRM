package cn.cordys.crm.customer.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.constants.MobileRuleScene;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.mobile.MobileRuleContext;
import cn.cordys.crm.customer.dto.mobile.MobileRuleDecision;
import cn.cordys.crm.system.dto.response.FieldRepeatCheckResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerMobileRuleService {

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private CustomerMobileRuleEngine customerMobileRuleEngine;
    @Resource
    private CustomerMobileConflictQueryService customerMobileConflictQueryService;

    /**
     * 保存客户时执行手机号冲突校验。
     *
     * @param customerId 当前客户ID
     * @param name 客户名称
     * @param mobile 手机号
     * @param createSource 客户来源
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     */
    public void validateForSave(String customerId, String name, String mobile, String createSource, String ownerId, String orgId) {
        MobileRuleDecision decision = customerMobileRuleEngine.evaluate(MobileRuleContext.builder()
                .customerId(customerId)
                .mobile(mobile)
                .createSource(createSource)
                .ownerId(ownerId)
                .orgId(orgId)
                .scene(MobileRuleScene.SAVE)
                .build());
        if (decision.isConflict()) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
        }
    }

    /**
     * 表单实时查重使用的手机号校验入口。
     *
     * @param resourceId 当前资源ID
     * @param mobile 手机号
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 查重结果
     */
    public FieldRepeatCheckResponse checkRepeat(String resourceId, String mobile, String ownerId, String orgId) {
        String excludeId = StringUtils.trimToNull(resourceId);
        String createSource = loadCreateSource(excludeId);
        MobileRuleDecision decision = customerMobileRuleEngine.evaluate(MobileRuleContext.builder()
                .customerId(excludeId)
                .mobile(mobile)
                .createSource(createSource)
                .ownerId(ownerId)
                .orgId(orgId)
                .scene(MobileRuleScene.FIELD_REPEAT_CHECK)
                .build());
        return FieldRepeatCheckResponse.builder()
                .repeat(decision.isConflict())
                .name(decision.getConflictCustomer() == null ? "" : StringUtils.defaultString(decision.getConflictCustomer().getName()))
                .build();
    }

    /**
     * 校验负责人接收客户时是否存在手机号冲突。
     *
     * @param customerId 当前客户ID
     * @param mobile 手机号
     * @param createSource 转入客户来源
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     */
    public void validateOwnerConflict(String customerId, String mobile, String createSource, String ownerId, String orgId) {
        MobileRuleDecision decision = customerMobileRuleEngine.evaluate(MobileRuleContext.builder()
                .customerId(customerId)
                .mobile(mobile)
                .createSource(createSource)
                .ownerId(ownerId)
                .orgId(orgId)
                .scene(MobileRuleScene.OWNER_RECEIVE)
                .build());
        if (decision.isConflict()) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
        }
    }

    /**
     * 加载负责人当前名下客户快照。
     *
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @param excludeCustomerId 需要排除的客户ID
     * @return 负责人名下客户列表
     */
    public List<Customer> listOwnerOwnedCustomers(String ownerId, String orgId, String excludeCustomerId) {
        return customerMobileConflictQueryService.listOwnerOwnedCustomers(ownerId, orgId, excludeCustomerId);
    }

    /**
     * 使用已加载的负责人客户快照判断接收冲突。
     *
     * @param mobile 手机号
     * @param ownerCustomers 负责人当前名下客户
     * @param createSource 转入客户来源
     * @return 是否存在冲突
     */
    public boolean hasOwnerReceiveConflict(String mobile, List<Customer> ownerCustomers, String createSource) {
        return customerMobileRuleEngine.hasOwnerReceiveConflict(mobile, ownerCustomers, createSource);
    }

    /**
     * 使用已预加载的冲突手机号集合判断“接收公海导入客户”是否冲突。
     *
     * @param mobile 手机号
     * @param ownerPrivateConflictMobiles 负责人名下手工创建冲突手机号集合
     * @param ownedPoolMobiles 负责人名下公海导入冲突手机号集合
     * @return 是否存在冲突
     */
    public boolean hasPoolImportReceiveConflict(String mobile, Set<String> ownerPrivateConflictMobiles, Set<String> ownedPoolMobiles) {
        String normalizedMobile = StringUtils.trimToNull(mobile);
        return normalizedMobile != null
                && (ownerPrivateConflictMobiles.contains(normalizedMobile) || ownedPoolMobiles.contains(normalizedMobile));
    }

    /**
     * 加载负责人名下、公海导入来源的手机号集合。
     *
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 手机号集合
     */
    public Set<String> loadOwnerPoolMobiles(String ownerId, String orgId) {
        return customerMobileConflictQueryService.listOwnerPoolMobiles(ownerId, orgId);
    }

    /**
     * 将当前客户手机号加入负责人已占用手机号集合。
     *
     * @param customer 当前客户
     * @param ownedMobiles 已占用手机号集合
     */
    public void addOwnedMobile(Customer customer, Set<String> ownedMobiles) {
        String mobile = customer == null ? null : StringUtils.trimToNull(customer.getMobile());
        if (mobile != null) {
            ownedMobiles.add(mobile);
        }
    }

    /**
     * 批量查询与私海来源客户冲突的手机号集合。
     *
     * @param mobiles 待校验手机号列表
     * @return 冲突手机号集合
     */
    public Set<String> findConflictMobilesForPrivateSources(List<String> mobiles) {
        List<String> validMobiles = getValidMobiles(mobiles);
        if (validMobiles.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Customer::getMobile, validMobiles)
                .in(Customer::getCreateSource, customerMobileRuleEngine.getPrivateRepeatSources());
        Long cutoffTime = customerMobileRuleEngine.getRepeatRuleCutoffTime();
        if (cutoffTime != null) {
            queryWrapper.gt(Customer::getCreateTime, cutoffTime);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 批量查询负责人名下、会阻断“接收公海导入客户”的手工创建手机号集合。
     * 该场景要求负责人名下不能同时存在同手机号的手工创建与公海导入客户，因此这里不受重复规则时间窗影响。
     *
     * @param mobiles 待校验手机号列表
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 冲突手机号集合
     */
    public Set<String> findPoolImportReceiveBlockingOwnerPrivateMobiles(List<String> mobiles, String ownerId, String orgId) {
        List<String> validMobiles = getValidMobiles(mobiles);
        if (validMobiles.isEmpty() || StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return Set.of();
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Customer::getMobile, validMobiles)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getCreateSource, customerMobileRuleEngine.getPrivateRepeatSources());
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 批量查询负责人名下、公海导入来源冲突的手机号集合。
     *
     * @param mobiles 待校验手机号列表
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 冲突手机号集合
     */
    public Set<String> findConflictMobilesForOwner(List<String> mobiles, String ownerId, String orgId) {
        List<String> validMobiles = getValidMobiles(mobiles);
        if (validMobiles.isEmpty() || StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return Set.of();
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Customer::getMobile, validMobiles)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .eq(Customer::getCreateSource, CustomerCreateSource.POOL_IMPORT);
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 获取字段查重场景下应使用的客户来源。
     *
     * @param excludeId 当前资源ID
     * @return 客户来源
     */
    private String loadCreateSource(String excludeId) {
        String createSource = CustomerCreateSource.MANUAL_CREATE;
        if (excludeId != null) {
            Customer current = customerMapper.selectByPrimaryKey(excludeId);
            if (current != null && StringUtils.isNotBlank(current.getCreateSource())) {
                createSource = current.getCreateSource();
            }
        }
        return createSource;
    }

    /**
     * 归一化并去重待校验手机号列表。
     *
     * @param mobiles 手机号列表
     * @return 清洗后的手机号列表
     */
    private List<String> getValidMobiles(List<String> mobiles) {
        return mobiles.stream()
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
