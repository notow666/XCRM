package cn.cordys.crm.customer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户列表按筛选批量任务内部数据结构。
 */
final class CustomerBatchTypes {

    private CustomerBatchTypes() {
    }

    record BatchChunkResult(int successCount, int failCount) {
    }

    record CustomerBatchTransferPlan(Map<String, List<String>> ownerToCustomerIds, int skippedCount) {
        static CustomerBatchTransferPlan empty() {
            return new CustomerBatchTransferPlan(new LinkedHashMap<>(), 0);
        }
    }

    record CustomerBatchToPoolPlan(List<String> customerIds, int skippedCount) {
    }
}
