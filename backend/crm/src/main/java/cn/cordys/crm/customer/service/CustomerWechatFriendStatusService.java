package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.system.domain.User;
import cn.cordys.mmba.dto.CustomerWxFriendStatusDTO;
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
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerWechatFriendStatusService {

    public static final int NOT_ADDED = 0;
    public static final int PENDING = 1;
    public static final int ADDED = 2;
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
    private ExtMmbaAuditMapper extMmbaAuditMapper;
    @Resource
    private MmbaDeviceService mmbaDeviceService;

    public void handleAddFriendReceipt(String um, String friendPhone, Integer processStatus, String userId) {
        if (processStatus == null || processStatus != PROCESS_STATUS_REQUEST_SENT) {
            return;
        }
        List<Customer> customers = listOwnedCustomers(um, friendPhone);
        if (CollectionUtils.isEmpty(customers)) {
            log.info("客户微信好友状态跳过，未找到匹配客户 um={} friendPhone={} processStatus={}",
                    um, friendPhone, processStatus);
            return;
        }
        for (Customer customer : customers) {
            int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
            if (currentStatus == ADDED) {
                continue;
            }
            updateStatus(customer, PENDING, userId);
        }
    }

    public void handleFriendChangeAudit(String um, String friendPhone, String isFriend, Integer operFlag, String userId) {
        List<Customer> customers = listOwnedCustomers(um, friendPhone);
        if (CollectionUtils.isEmpty(customers)) {
            log.info("客户微信好友状态跳过，未找到匹配客户 um={} friendPhone={} isFriend={} operFlag={}",
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
        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null) {
            return;
        }
        updateStatus(customer, calculateStatus(customer), userId);
    }

    public void recalculateCustomers(Collection<String> customerIds, String userId) {
        if (CollectionUtils.isEmpty(customerIds)) {
            return;
        }
        List<Customer> customers = customerMapper.selectByIds(customerIds.toArray(new String[0]));
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        for (Customer customer : customers) {
            updateStatus(customer, calculateStatus(customer), userId);
        }
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
        for (Customer customer : customers) {
            updateStatus(customer, calculateStatus(customer), userId);
        }
    }

    private int calculateStatus(Customer customer) {
        String mobile = customer == null ? null : StringUtils.trimToNull(customer.getMobile());
        if (mobile == null) {
            return NOT_ADDED;
        }
        String ownerUm = readOwnerUm(customer.getOwner());
        if (ownerUm == null) {
            return NOT_ADDED;
        }
        List<String> activeWxIds = mmbaDeviceService.listMappingsByUm(ownerUm).stream()
                .filter(mapping -> StringUtils.equals("ACTIVE", mapping.getMappingStatus()))
                .map(mapping -> StringUtils.trimToNull(mapping.getWxid()))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(activeWxIds)) {
            return NOT_ADDED;
        }
        List<CustomerWxFriendStatusDTO> friendStatuses = extMmbaAuditMapper
                .listCustomerWxFriendStatus(List.of(mobile), ownerUm, activeWxIds);
        if (CollectionUtils.isNotEmpty(friendStatuses)) {
            Integer wxFriendAdded = friendStatuses.getFirst().getWxFriendAdded();
            return Objects.equals(wxFriendAdded, 1) ? ADDED : NOT_ADDED;
        }
        Boolean pending = extMmbaAuditMapper.existsPendingAddFriendReceipt(ownerUm, mobile, activeWxIds);
        return Boolean.TRUE.equals(pending) ? PENDING : NOT_ADDED;
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

    private String readOwnerUm(String ownerId) {
        if (StringUtils.isBlank(ownerId)) {
            return null;
        }
        User owner = userBaseMapper.selectByPrimaryKey(ownerId);
        return owner == null ? null : StringUtils.trimToNull(owner.getUm());
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

    private void updateStatus(Customer customer, int targetStatus, String userId) {
        if (customer == null) {
            return;
        }
        int currentStatus = normalizeStatus(customer.getWechatFriendStatus());
        if (currentStatus == targetStatus) {
            return;
        }
        Customer update = new Customer();
        update.setId(customer.getId());
        update.setWechatFriendStatus(targetStatus);
        customerMapper.updateById(update);
        customer.setWechatFriendStatus(targetStatus);
        log.info("客户微信好友状态更新 customerId={} mobile={} from={} to={}",
                customer.getId(), customer.getMobile(), currentStatus, targetStatus);
    }
}
