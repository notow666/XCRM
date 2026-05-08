package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void upgradeByCustomer(String customerId, String customerTel, Integer targetStatus, String userId) {
        if (targetStatus == null || targetStatus < DIALED_NOT_CONNECTED) {
            return;
        }
        Customer customer = findCustomer(customerId, customerTel);
        if (customer == null) {
            log.warn("客户拨打状态更新跳过，未找到客户 customerId={} customerTel={} targetStatus={}",
                    customerId, customerTel, targetStatus);
            return;
        }
        int currentStatus = customer.getCallStatus() == null ? NOT_DIALED : customer.getCallStatus();
        if (currentStatus >= targetStatus) {
            return;
        }
        Customer update = new Customer();
        update.setId(customer.getId());
        update.setCallStatus(targetStatus);
        update.setUpdateTime(System.currentTimeMillis());
        update.setUpdateUser(userId);
        customerMapper.updateById(update);
        log.info("客户拨打状态升级 customerId={} customerTel={} from={} to={}",
                customer.getId(), customer.getMobile(), currentStatus, targetStatus);
    }

    private Customer findCustomer(String customerId, String customerTel) {
        if (StringUtils.isNotBlank(customerId)) {
            return customerMapper.selectByPrimaryKey(customerId);
        }
        String mobile = StringUtils.trimToNull(customerTel);
        if (mobile == null) {
            return null;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getMobile, mobile);
        return customerMapper.selectListByLambda(wrapper).stream().findFirst().orElse(null);
    }
}
