package cn.cordys.crm.customer.service;

import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerOwner;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公海批量任务内部数据结构（按筛选 / 勾选 ID 批量共用）。
 */
final class PoolCustomerBatchTypes {

    private PoolCustomerBatchTypes() {
    }

    record BatchChunkResult(int successCount, int failCount) {
    }

    record AssignChunkTask(String ownerId, List<Customer> customers, Map<String, String> recentOwnerMap) {
    }

    record BatchPickPreparedData(Map<String, CustomerOwner> lastOwnerMap,
                               Set<String> privateConflictMobiles,
                               Set<String> ownedPoolMobiles,
                               int remainingCapacity,
                               Integer remainingDailyPick) {
    }

    record BatchPickPlan(List<Customer> pickedCustomers, List<String> skippedCustomerIds) {
    }

    record BatchAssignPreparedData(Map<String, Integer> userCapacitiesMap,
                                 Map<String, Set<String>> userOwnedPoolMobileMap,
                                 Map<String, Set<String>> userPrivateConflictMobileMap) {
    }

    record BatchTransferPlan(List<Customer> transferCustomers, List<String> skippedCustomerIds) {
    }

    static final class BatchAssignPlan {

        private final Map<String, List<Customer>> ownerCustomersMap;
        private final List<String> assignedCustomerIds;
        private final List<String> unassignedCustomerIds;
        private final Map<String, String> recentOwnerMap;

        BatchAssignPlan(Map<String, List<Customer>> ownerCustomersMap, List<String> assignedCustomerIds,
                        List<String> unassignedCustomerIds, Map<String, String> recentOwnerMap) {
            this.ownerCustomersMap = ownerCustomersMap;
            this.assignedCustomerIds = assignedCustomerIds;
            this.unassignedCustomerIds = unassignedCustomerIds;
            this.recentOwnerMap = recentOwnerMap;
        }

        Map<String, List<Customer>> getOwnerCustomersMap() {
            return ownerCustomersMap;
        }

        List<String> getAssignedCustomerIds() {
            return assignedCustomerIds;
        }

        List<String> getUnassignedCustomerIds() {
            return unassignedCustomerIds;
        }

        Map<String, String> getRecentOwnerMap() {
            return recentOwnerMap;
        }
    }
}
