package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.domain.User;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户拨打状态维护。
 * 仅允许状态升级：0 -> 1 -> 2，不允许降级。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerCallStatusService {

    public static final int NOT_DIALED = 0;
    public static final int DIALED_NOT_CONNECTED = 1;
    public static final int DIALED_CONNECTED = 2;

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    public void upgradeByCustomer(String customerId, String customerTel, String um, Integer targetStatus, String userId) {
        if (targetStatus == null || targetStatus < DIALED_NOT_CONNECTED) {
            return;
        }
        Customer customer = findCustomer(customerId, customerTel, um);
        if (customer == null) {
            log.warn("客户拨打状态更新跳过，未找到客户 customerId={} customerTel={} um={} targetStatus={}",
                    customerId, customerTel, um, targetStatus);
            return;
        }
        int currentStatus = customer.getCallStatus() == null ? NOT_DIALED : customer.getCallStatus();
        if (currentStatus >= targetStatus) {
            return;
        }
        extCustomerMapper.updateCallStatusById(customer.getId(), targetStatus);
        log.info("客户拨打状态升级 customerId={} customerTel={} from={} to={}",
                customer.getId(), customer.getMobile(), currentStatus, targetStatus);
    }

    private Customer findCustomer(String customerId, String customerTel, String um) {
        if (StringUtils.isNotBlank(customerId)) {
            return customerMapper.selectByPrimaryKey(customerId);
        }
        String mobile = StringUtils.trimToNull(customerTel);
        String normalizedUm = StringUtils.trimToNull(um);
        if (mobile == null || normalizedUm == null) {
            return null;
        }
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUm, normalizedUm);
        List<String> ownerIds = userBaseMapper.selectListByLambda(userWrapper).stream()
                .map(User::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getMobile, mobile)
                .eq(Customer::getInSharedPool, false)
                .in(Customer::getOwner, ownerIds);
        return customerMapper.selectListByLambda(wrapper).stream().findFirst().orElse(null);
    }
}
