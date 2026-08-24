package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.domain.Customer;

import java.util.List;

public record CustomerDeleteBatchResult(
        List<Customer> deletedCustomers,
        List<String> failedCustomerIds
) {

    public int successCount() {
        return deletedCustomers.size();
    }

    public int failCount() {
        return failedCustomerIds.size();
    }
}
