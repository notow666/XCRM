package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.constants.CustomerContractDeletePolicyType;
import cn.cordys.crm.customer.domain.Customer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 无事务删除编排器。批次开始时固定策略，随后顺序执行逐客户短事务。
 */
@Slf4j
@Service
public class CustomerDeleteOrchestrator {

    @Resource
    private CustomerDeleteUnitService deleteUnitService;
    @Resource
    private CustomerContractDeletePolicyService contractDeletePolicyService;
    @Resource
    private CustomerContractCascadeDeleteService contractCascadeDeleteService;

    public Customer deleteOne(String orgId, String customerId, String operator,
                              String customerLogModule, String reason) {
        cascadeContractsIfNecessary(contractDeletePolicyService.getPolicy(orgId), orgId,
                customerId, operator, reason, CustomerDeleteScene.MANUAL, null);
        return deleteUnitService.deleteOne(customerId, orgId, operator,
                customerLogModule, reason, CustomerDeleteScene.MANUAL, null);
    }

    public CustomerDeleteBatchResult delete(String orgId, List<String> customerIds, String operator,
                                            String customerLogModule, String reason,
                                            CustomerDeleteScene scene, Long cutoffTime) {
        if (CollectionUtils.isEmpty(customerIds)) {
            return new CustomerDeleteBatchResult(List.of(), List.of());
        }
        List<Customer> deletedCustomers = new ArrayList<>();
        List<String> failedCustomerIds = new ArrayList<>();
        String policy = contractDeletePolicyService.getPolicy(orgId);
        for (String customerId : customerIds.stream().distinct().toList()) {
            try {
                cascadeContractsIfNecessary(policy, orgId, customerId, operator, reason, scene, cutoffTime);
                deletedCustomers.add(deleteUnitService.deleteOne(customerId, orgId, operator,
                        customerLogModule, reason, scene, cutoffTime));
            } catch (Exception ex) {
                failedCustomerIds.add(customerId);
                log.error("[CUSTOMER_DELETE_FAILED] customerId={}, orgId={}, scene={}",
                        customerId, orgId, scene, ex);
            }
        }
        return new CustomerDeleteBatchResult(List.copyOf(deletedCustomers), List.copyOf(failedCustomerIds));
    }

    private void cascadeContractsIfNecessary(String policy, String orgId, String customerId,
                                             String operator, String reason, CustomerDeleteScene scene,
                                             Long cutoffTime) {
        if (CustomerContractDeletePolicyType.CASCADE.equals(policy)) {
            deleteUnitService.validateOne(customerId, orgId, scene, cutoffTime);
            contractCascadeDeleteService.deleteByCustomer(customerId, orgId, operator, reason);
        }
    }
}
