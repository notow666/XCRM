package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerAutoDeleteBatchService {

    @Resource
    private CustomerDeleteOrchestrator customerDeleteOrchestrator;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    public int deletePoolBatch(List<String> ids, String orgId, long cutoffTime, String operator,
                               String reason) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Customer::getId, ids)
                .eq(Customer::getCreateSource, CustomerCreateSource.POOL_IMPORT)
                .ltT(Customer::getCreateTime, cutoffTime);
        List<String> candidateIds = customerMapper.selectListByLambda(wrapper).stream().map(Customer::getId).toList();
        return customerDeleteOrchestrator.delete(orgId, candidateIds, operator,
                LogModule.CUSTOMER_INDEX, reason, CustomerDeleteScene.POOL_AUTO, cutoffTime).successCount();
    }

    public int deletePrivateBatch(List<String> ids, String orgId, long cutoffTime, String operator,
                                  String reason) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        List<Customer> customers = extCustomerMapper.listPrivateAutoDeleteCandidates(
                orgId, cutoffTime, 0, ids
        );
        List<String> candidateIds = customers.stream().map(Customer::getId).toList();
        return customerDeleteOrchestrator.delete(orgId, candidateIds, operator,
                LogModule.CUSTOMER_INDEX, reason, CustomerDeleteScene.PRIVATE_AUTO, cutoffTime).successCount();
    }
}
