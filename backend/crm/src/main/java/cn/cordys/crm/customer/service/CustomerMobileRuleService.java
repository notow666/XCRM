package cn.cordys.crm.customer.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerRepeatRuleConfig;
import cn.cordys.crm.system.dto.response.FieldRepeatCheckResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerMobileRuleService {

    private static final Set<String> PRIVATE_REPEAT_SOURCES = new HashSet<>(
            Arrays.asList(CustomerCreateSource.MANUAL_CREATE, CustomerCreateSource.PRIVATE_IMPORT)
    );

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private CustomerRepeatRuleConfigService customerRepeatRuleConfigService;

    public void validateForSave(String customerId, String name, String mobile, String createSource, String ownerId, String orgId) {
        String normalizedMobile = StringUtils.trimToNull(mobile);
        if (normalizedMobile == null) {
            return;
        }
        if (usesPrivateRepeatRule(createSource)) {
            Customer conflict = findPrivateSourceConflict(customerId, normalizedMobile);
            if (conflict != null) {
                throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
            }
            if (hasOwnerPoolConflict(customerId, normalizedMobile, ownerId, orgId)) {
                throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
            }
            return;
        }
        if (existsAnySourceConflict(customerId, normalizedMobile)) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
        }
    }

    public FieldRepeatCheckResponse checkRepeat(String resourceId, String mobile, String ownerId, String orgId) {
        String normalizedMobile = StringUtils.trimToNull(mobile);
        if (normalizedMobile == null) {
            return FieldRepeatCheckResponse.builder().repeat(false).name("").build();
        }
        String createSource = CustomerCreateSource.MANUAL_CREATE;
        String excludeId = StringUtils.trimToNull(resourceId);
        if (excludeId != null) {
            Customer current = customerMapper.selectByPrimaryKey(excludeId);
            if (current != null && StringUtils.isNotBlank(current.getCreateSource())) {
                createSource = current.getCreateSource();
            }
        }
        Customer conflict = usesPrivateRepeatRule(createSource)
                ? findPrivateSourceConflict(excludeId, normalizedMobile)
                : findAnySourceConflict(excludeId, normalizedMobile);
        if (conflict == null && usesPrivateRepeatRule(createSource)) {
            conflict = findOwnerPoolConflict(excludeId, normalizedMobile, ownerId, orgId);
        }
        return FieldRepeatCheckResponse.builder()
                .repeat(conflict != null)
                .name(conflict == null ? "" : StringUtils.defaultString(conflict.getName()))
                .build();
    }

    public void validateOwnerConflict(String customerId, String mobile, String ownerId, String orgId) {
        String normalizedMobile = StringUtils.trimToNull(mobile);
        if (normalizedMobile == null) {
            return;
        }
        if (hasOwnerPoolConflict(customerId, normalizedMobile, ownerId, orgId)) {
            throw new GenericException(Translator.getWithArgs("common.field_value.repeat", "手机号"));
        }
    }

    public Set<String> findConflictMobilesForPrivateSources(List<String> mobiles) {
        List<String> validMobiles = getValidMobiles(mobiles);
        if (validMobiles.isEmpty()) {
            return Set.of();
        }
        CustomerRepeatRuleConfig config = customerRepeatRuleConfigService.getConfigEntity();
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Customer::getMobile, validMobiles)
                .in(Customer::getCreateSource, PRIVATE_REPEAT_SOURCES);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            long cutoffTime = System.currentTimeMillis() - config.getRepeatAfterDays() * 24L * 60L * 60L * 1000L;
            queryWrapper.gt(Customer::getCreateTime, cutoffTime);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    public Set<String> findConflictMobilesForOwnerPrivateSources(List<String> mobiles, String ownerId, String orgId) {
        List<String> validMobiles = getValidMobiles(mobiles);
        if (validMobiles.isEmpty() || StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return Set.of();
        }
        CustomerRepeatRuleConfig config = customerRepeatRuleConfigService.getConfigEntity();
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Customer::getMobile, validMobiles)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getCreateSource, PRIVATE_REPEAT_SOURCES);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            long cutoffTime = System.currentTimeMillis() - config.getRepeatAfterDays() * 24L * 60L * 60L * 1000L;
            queryWrapper.gt(Customer::getCreateTime, cutoffTime);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

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

    private Customer findPrivateSourceConflict(String excludeId, String mobile) {
        CustomerRepeatRuleConfig config = customerRepeatRuleConfigService.getConfigEntity();
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile)
                .in(Customer::getCreateSource, PRIVATE_REPEAT_SOURCES);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            long cutoffTime = System.currentTimeMillis() - config.getRepeatAfterDays() * 24L * 60L * 60L * 1000L;
            queryWrapper.gt(Customer::getCreateTime, cutoffTime);
        }
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        List<Customer> customers = customerMapper.selectListByLambda(queryWrapper);
        return customers.stream().findFirst().orElse(null);
    }

    private boolean existsAnySourceConflict(String excludeId, String mobile) {
        return findAnySourceConflict(excludeId, mobile) != null;
    }

    private Customer findAnySourceConflict(String excludeId, String mobile) {
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile);
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        List<Customer> customers = customerMapper.selectListByLambda(queryWrapper);
        return customers.stream().findFirst().orElse(null);
    }

    private boolean hasOwnerPoolConflict(String excludeId, String mobile, String ownerId, String orgId) {
        return findOwnerPoolConflict(excludeId, mobile, ownerId, orgId) != null;
    }

    private Customer findOwnerPoolConflict(String excludeId, String mobile, String ownerId, String orgId) {
        if (StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return null;
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .eq(Customer::getCreateSource, CustomerCreateSource.POOL_IMPORT);
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    private boolean usesPrivateRepeatRule(String createSource) {
        return PRIVATE_REPEAT_SOURCES.contains(createSource);
    }

    private List<String> getValidMobiles(List<String> mobiles) {
        return mobiles.stream()
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
