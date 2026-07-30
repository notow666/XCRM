package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerAutoDeleteBatchService {

    @Resource
    private CustomerService customerService;
    @Resource
    private LogService logService;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    @Transactional(rollbackFor = Exception.class)
    public int deletePoolBatch(List<String> ids, long cutoffTime, String operator, String reason) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Customer::getId, ids)
                .eq(Customer::getCreateSource, CustomerCreateSource.POOL_IMPORT)
                .ltT(Customer::getCreateTime, cutoffTime);
        return deleteCustomers(customerMapper.selectListByLambda(wrapper), operator, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deletePrivateBatch(List<String> ids, String orgId, long cutoffTime, String operator, String reason) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        List<Customer> customers = extCustomerMapper.listPrivateAutoDeleteCandidates(
                orgId, cutoffTime, 0, ids
        );
        return deleteCustomers(customers, operator, reason);
    }

    private int deleteCustomers(List<Customer> customers, String operator, String reason) {
        if (CollectionUtils.isEmpty(customers)) {
            return 0;
        }
        List<String> customerIds = customers.stream().map(Customer::getId).toList();
        customerService.deleteCustomerResource(customerIds);
        List<LogDTO> logs = customers.stream().map(customer -> {
            LogDTO logDTO = new LogDTO(
                    customer.getOrganizationId(),
                    customer.getId(),
                    operator,
                    LogType.DELETE,
                    LogModule.CUSTOMER_INDEX,
                    customer.getName()
            );
            logDTO.setDetail(reason);
            return logDTO;
        }).toList();
        logService.batchAddSync(logs);
        return customerIds.size();
    }
}
