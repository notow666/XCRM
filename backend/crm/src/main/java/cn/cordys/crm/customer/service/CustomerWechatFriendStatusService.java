package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.domain.User;
import cn.cordys.mmba.mapper.ExtMmbaAuditMapper;
import cn.cordys.mmba.service.MmbaDeviceService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerWechatFriendStatusService {

    public static final int REQUEST_INITIATED = -1;
    public static final int NOT_ADDED = 0;
    public static final int PENDING = 1;
    public static final int ADDED = 2;
    public static final int PROCESS_STATUS_ALREADY_FRIEND = 3;
    public static final int PROCESS_STATUS_REQUEST_SENT = 4;
    private static final int OPER_FLAG_ADD_CONFIRMED = 1;
    private static final int OPER_FLAG_DELETE_FRIEND = 2;
    private static final int OPER_FLAG_EDIT_FRIEND = 3;
    private static final int OPER_FLAG_OTHER_ADD_ME = 4;
    private static final int OPER_FLAG_I_ADD_OTHER = 5;
    private static final int OPER_FLAG_ADD_BLACKLIST = 6;
    private static final int OPER_FLAG_REMOVE_BLACKLIST = 7;

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private ExtMmbaAuditMapper extMmbaAuditMapper;
    @Resource
    private MmbaDeviceService mmbaDeviceService;

    public void markAddInitiated(String customerId) {
        if (StringUtils.isBlank(customerId)) {
            return;
        }
        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null || Boolean.TRUE.equals(customer.getInSharedPool())) {
            return;
        }
        int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
        if (currentStatus != NOT_ADDED) {
            return;
        }
        extCustomerMapper.updateWechatFriendStatusById(customer.getId(), REQUEST_INITIATED);
        customer.setWechatFriendStatus(REQUEST_INITIATED);
        log.info("客户微信好友状态更新为已发起添加 customerId={} mobile={} from={} to={}",
                customer.getId(), customer.getMobile(), currentStatus, REQUEST_INITIATED);
    }

    public void handleAddFriendReceipt(String um, String friendPhone, Integer processStatus, String userId) {
        Integer targetStatus = resolveAddFriendReceiptStatus(processStatus);
        if (targetStatus == null) {
            return;
        }
        List<Customer> customers = listOwnedCustomers(um, friendPhone);
        if (CollectionUtils.isEmpty(customers)) {
            log.warn(CrmLoggers.MMBA_CALLBACK_MARKER, "客户微信好友状态跳过，未找到匹配客户 um={} friendPhone={} processStatus={}",
                    um, friendPhone, processStatus);
            return;
        }
        for (Customer customer : customers) {
            int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
            if (currentStatus == targetStatus) {
                continue;
            }
            updateStatus(customer, targetStatus, userId);
        }
    }

    public void handleFriendChangeAudit(String um, String friendPhone, String isFriend, Integer operFlag, String userId) {
        List<Customer> customers = listOwnedCustomers(um, friendPhone);
        if (CollectionUtils.isEmpty(customers)) {
            log.warn(CrmLoggers.MMBA_CALLBACK_MARKER, "客户微信好友状态跳过，未找到匹配客户 um={} friendPhone={} isFriend={} operFlag={}",
                    um, friendPhone, isFriend, operFlag);
            return;
        }
        Integer targetStatus = resolveFriendChangeStatus(isFriend, operFlag);
        if (targetStatus == null) {
            return;
        }
        for (Customer customer : customers) {
            updateStatus(customer, targetStatus, userId);
        }
    }

    public void resetToNotAdded(String customerId, String userId) {
        if (StringUtils.isBlank(customerId)) {
            return;
        }
        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null) {
            return;
        }
        updateStatus(customer, NOT_ADDED, userId);
    }

    public void recalculateCustomer(String customerId, String userId) {
        if (StringUtils.isBlank(customerId)) {
            return;
        }
        recalculateCustomers(List.of(customerId), userId);
    }

    public void recalculateCustomers(Collection<String> customerIds, String userId) {
        if (CollectionUtils.isEmpty(customerIds)) {
            return;
        }
        List<Customer> customers = customerMapper.selectByIds(customerIds.toArray(new String[0]));
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        recalculateCustomers(customers, userId);
    }

    public void recalculateByUm(String um, String userId) {
        List<String> ownerIds = listUserIdsByUm(um);
        if (CollectionUtils.isEmpty(ownerIds)) {
            return;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Customer::getOwner, ownerIds)
                .eq(Customer::getInSharedPool, false);
        List<Customer> customers = customerMapper.selectListByLambda(wrapper);
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        recalculateCustomers(customers, userId);
    }

    private void recalculateCustomers(List<Customer> customers, String userId) {
        Map<String, Integer> targetStatusMap = calculateTargetStatusMap(customers);
        applyTargetStatuses(customers, targetStatusMap, userId);
    }

    private Map<String, Integer> calculateTargetStatusMap(List<Customer> customers) {
        Map<String, Integer> targetStatusMap = new HashMap<>();
        Map<String, List<Customer>> customersByOwner = new HashMap<>();
        Set<String> ownerIds = new HashSet<>();

        for (Customer customer : customers) {
            if (customer == null || StringUtils.isBlank(customer.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(customer.getInSharedPool())) {
                targetStatusMap.put(customer.getId(), NOT_ADDED);
                continue;
            }
            String mobile = StringUtils.trimToNull(customer.getMobile());
            String ownerId = StringUtils.trimToNull(customer.getOwner());
            if (mobile == null || ownerId == null) {
                targetStatusMap.put(customer.getId(), NOT_ADDED);
                continue;
            }
            customersByOwner.computeIfAbsent(ownerId, key -> new java.util.ArrayList<>()).add(customer);
            ownerIds.add(ownerId);
        }

        // 先按负责人分组聚合上下文，避免同一 owner 下重复查询 UM 和激活微信号映射。
        Map<String, String> ownerUmMap = buildOwnerUmMap(ownerIds);
        Map<String, List<String>> activeWxIdsByUm = new HashMap<>();

        for (Map.Entry<String, List<Customer>> entry : customersByOwner.entrySet()) {
            String ownerId = entry.getKey();
            List<Customer> ownerCustomers = entry.getValue();
            String ownerUm = ownerUmMap.get(ownerId);
            if (ownerUm == null) {
                ownerCustomers.forEach(customer -> targetStatusMap.put(customer.getId(), NOT_ADDED));
                continue;
            }

            List<String> activeWxIds = activeWxIdsByUm.computeIfAbsent(ownerUm, this::listActiveWxIdsByUm);
            if (CollectionUtils.isEmpty(activeWxIds)) {
                ownerCustomers.forEach(customer -> targetStatusMap.put(customer.getId(), NOT_ADDED));
                continue;
            }

            List<String> mobiles = ownerCustomers.stream()
                    .map(Customer::getMobile)
                    .map(StringUtils::trimToNull)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .toList();
            if (CollectionUtils.isEmpty(mobiles)) {
                ownerCustomers.forEach(customer -> targetStatusMap.put(customer.getId(), NOT_ADDED));
                continue;
            }

            // 同一负责人下的手机号批量查询“已加好友”和“待添加”集合，再在内存中归并目标状态。
            Set<String> addedPhones = new HashSet<>(extMmbaAuditMapper.listAddedFriendPhones(mobiles, ownerUm, activeWxIds));
            Set<String> pendingPhones = new HashSet<>(extMmbaAuditMapper.listPendingAddFriendPhones(mobiles, ownerUm, activeWxIds));

            for (Customer customer : ownerCustomers) {
                String mobile = StringUtils.trimToNull(customer.getMobile());
                if (mobile == null) {
                    targetStatusMap.put(customer.getId(), NOT_ADDED);
                    continue;
                }
                if (addedPhones.contains(mobile)) {
                    targetStatusMap.put(customer.getId(), ADDED);
                } else if (pendingPhones.contains(mobile)) {
                    targetStatusMap.put(customer.getId(), PENDING);
                } else {
                    targetStatusMap.put(customer.getId(), NOT_ADDED);
                }
            }
        }
        return targetStatusMap;
    }

    private Map<String, String> buildOwnerUmMap(Set<String> ownerIds) {
        if (CollectionUtils.isEmpty(ownerIds)) {
            return Map.of();
        }
        List<User> users = userBaseMapper.selectByIds(ownerIds.toArray(new String[0]));
        if (CollectionUtils.isEmpty(users)) {
            return Map.of();
        }
        Map<String, String> ownerUmMap = new HashMap<>();
        for (User user : users) {
            if (user == null || StringUtils.isBlank(user.getId())) {
                continue;
            }
            ownerUmMap.put(user.getId(), StringUtils.trimToNull(user.getUm()));
        }
        return ownerUmMap;
    }

    private List<String> listActiveWxIdsByUm(String um) {
        return mmbaDeviceService.listMappingsByUm(um).stream()
                .filter(mapping -> StringUtils.equals("ACTIVE", mapping.getMappingStatus()))
                .map(mapping -> StringUtils.trimToNull(mapping.getWxid()))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private void applyTargetStatuses(List<Customer> customers, Map<String, Integer> targetStatusMap, String userId) {
        Map<Integer, List<String>> idsByTargetStatus = new HashMap<>();
        for (Customer customer : customers) {
            if (customer == null || StringUtils.isBlank(customer.getId())) {
                continue;
            }
            Integer targetStatus = targetStatusMap.get(customer.getId());
            if (targetStatus == null) {
                continue;
            }
            int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
            if (currentStatus == targetStatus) {
                continue;
            }
            idsByTargetStatus.computeIfAbsent(targetStatus, key -> new java.util.ArrayList<>()).add(customer.getId());
            customer.setWechatFriendStatus(targetStatus);
        }

        // 目标状态只有 0/1/2 三种，按状态分桶后批量更新。
        for (Map.Entry<Integer, List<String>> entry : idsByTargetStatus.entrySet()) {
            extCustomerMapper.batchUpdateWechatFriendStatusByIds(entry.getValue(), entry.getKey());
            log.info("客户微信好友状态批量更新 targetStatus={} count={}", entry.getKey(), entry.getValue().size());
        }
    }

    private List<Customer> listOwnedCustomers(String um, String friendPhone) {
        String mobile = StringUtils.trimToNull(friendPhone);
        if (StringUtils.isBlank(um) || mobile == null) {
            return List.of();
        }
        List<String> ownerIds = listUserIdsByUm(um);
        if (CollectionUtils.isEmpty(ownerIds)) {
            return List.of();
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Customer::getOwner, ownerIds)
                .eq(Customer::getMobile, mobile)
                .eq(Customer::getInSharedPool, false);
        return customerMapper.selectListByLambda(wrapper);
    }

    private List<String> listUserIdsByUm(String um) {
        String normalizedUm = StringUtils.trimToNull(um);
        if (normalizedUm == null) {
            return List.of();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUm, normalizedUm);
        return userBaseMapper.selectListByLambda(wrapper).stream()
                .map(User::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private int normalizeStatus(Integer status) {
        return status == null ? NOT_ADDED : status;
    }

    private Integer resolveFriendChangeStatus(String isFriend, Integer operFlag) {
        if ("1".equals(StringUtils.trimToEmpty(isFriend))) {
            return ADDED;
        }
        if (operFlag == null) {
            return NOT_ADDED;
        }
        return switch (operFlag) {
            case OPER_FLAG_ADD_CONFIRMED -> ADDED;
            case OPER_FLAG_OTHER_ADD_ME, OPER_FLAG_I_ADD_OTHER -> PENDING;
            case OPER_FLAG_DELETE_FRIEND, OPER_FLAG_ADD_BLACKLIST, OPER_FLAG_REMOVE_BLACKLIST -> NOT_ADDED;
            case OPER_FLAG_EDIT_FRIEND -> null;
            default -> NOT_ADDED;
        };
    }

    private Integer resolveAddFriendReceiptStatus(Integer processStatus) {
        if (processStatus == null) {
            return null;
        }
        return switch (processStatus) {
            case PROCESS_STATUS_ALREADY_FRIEND -> ADDED;
            case PROCESS_STATUS_REQUEST_SENT -> PENDING;
            default -> null;
        };
    }

    private void updateStatus(Customer customer, int targetStatus, String userId) {
        if (customer == null) {
            return;
        }
        int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
        if (currentStatus == targetStatus) {
            return;
        }
        extCustomerMapper.updateWechatFriendStatusById(customer.getId(), targetStatus);
        customer.setWechatFriendStatus(targetStatus);
        log.info("客户微信好友状态更新 customerId={} mobile={} from={} to={}",
                customer.getId(), customer.getMobile(), currentStatus, targetStatus);
    }
}
