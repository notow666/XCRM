package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手机号冲突查询服务。
 * 仅负责从数据库收集候选冲突数据，不承载规则判定。
 */
@Service
public class CustomerMobileConflictQueryService {

    @Resource
    private BaseMapper<Customer> customerMapper;

    /**
     * 查询负责人当前名下的全部私海客户。
     *
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @param excludeCustomerId 需要排除的客户ID
     * @return 当前负责人名下客户列表
     */
    public List<Customer> listOwnerOwnedCustomers(String ownerId, String orgId, String excludeCustomerId) {
        if (StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false);
        if (StringUtils.isNotBlank(excludeCustomerId)) {
            queryWrapper.nq(Customer::getId, excludeCustomerId);
        }
        return new ArrayList<>(customerMapper.selectListByLambda(queryWrapper));
    }

    /**
     * 查询私海来源客户的手机号冲突。
     *
     * @param excludeId 需要排除的客户ID
     * @param mobile 手机号
     * @param privateRepeatSources 受私海重复规则约束的来源集合
     * @param cutoffTime 规则开启时的时间阈值；为 null 表示不过滤时间
     * @return 冲突客户，不存在则返回 null
     */
    public Customer findPrivateSourceConflict(String excludeId, String mobile, List<String> privateRepeatSources, Long cutoffTime) {
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile)
                .in(Customer::getCreateSource, privateRepeatSources);
        if (cutoffTime != null) {
            queryWrapper.gt(Customer::getCreateTime, cutoffTime);
        }
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    /**
     * 查询负责人名下、公海导入来源的手机号冲突。
     *
     * @param excludeId 需要排除的客户ID
     * @param mobile 手机号
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 冲突客户，不存在则返回 null
     */
    public Customer findOwnerPoolConflict(String excludeId, String mobile, String ownerId, String orgId) {
        if (StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return null;
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getCreateSource, CustomerCreateSource.poolSourceTypes());
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    /**
     * 查询公海导入来源客户的手机号冲突。
     *
     * @param excludeId 需要排除的客户ID
     * @param mobile 手机号
     * @param orgId 组织ID
     * @return 冲突客户，不存在则返回 null
     */
    public Customer findPoolImportConflict(String excludeId, String mobile, String orgId) {
        if (StringUtils.isBlank(orgId)) {
            return null;
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getMobile, mobile)
                .in(Customer::getCreateSource, CustomerCreateSource.poolSourceTypes());
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    /**
     * 查询负责人名下、手工创建来源客户的手机号冲突。
     * 该查询用于 POOL_IMPORT 客户的保存、查重与接收场景，不受重复规则时间窗影响。
     *
     * @param excludeId 需要排除的客户ID
     * @param mobile 手机号
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @param privateRepeatSources 手工创建来源集合
     * @return 冲突客户，不存在则返回 null
     */
    public Customer findOwnerPrivateConflict(String excludeId, String mobile, String ownerId, String orgId, List<String> privateRepeatSources) {
        if (StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return null;
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getMobile, mobile)
                .eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getCreateSource, privateRepeatSources);
        if (StringUtils.isNotBlank(excludeId)) {
            queryWrapper.nq(Customer::getId, excludeId);
        }
        return customerMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    /**
     * 查询负责人名下、公海导入来源的手机号集合。
     *
     * @param ownerId 负责人ID
     * @param orgId 组织ID
     * @return 手机号集合
     */
    public Set<String> listOwnerPoolMobiles(String ownerId, String orgId) {
        if (StringUtils.isBlank(ownerId) || StringUtils.isBlank(orgId)) {
            return new HashSet<>();
        }
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getOwner, ownerId)
                .eq(Customer::getOrganizationId, orgId)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getCreateSource, CustomerCreateSource.poolSourceTypes());
        return customerMapper.selectListByLambda(queryWrapper).stream()
                .map(Customer::getMobile)
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
