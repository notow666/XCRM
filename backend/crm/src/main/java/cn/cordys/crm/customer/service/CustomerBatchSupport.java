package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerCapacity;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.customer.dto.request.CustomerBatchTransferByConditionRequest;
import cn.cordys.crm.customer.dto.request.CustomerBatchTransferRequest;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.request.BatchPoolReasonRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.crm.system.dto.response.BatchAffectResponse;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.cordys.crm.customer.service.CustomerBatchTypes.BatchChunkResult;
import static cn.cordys.crm.customer.service.CustomerBatchTypes.CustomerBatchTransferPlan;
import static cn.cordys.crm.customer.service.CustomerBatchTypes.CustomerBatchToPoolPlan;

@Slf4j
@Service
public class CustomerBatchSupport {

    @Resource
    private PoolCustomerService poolCustomerService;
    @Resource
    private CustomerMobileRuleService customerMobileRuleService;
    @Resource
    private CustomerStageService customerStageService;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private CustomerPoolService customerPoolService;
    @Resource
    private BaseMapper<CustomerPool> customerPoolMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    public List<List<String>> partition(List<String> ids, int batchSize) {
        if (CollectionUtils.isEmpty(ids) || batchSize <= 0) {
            return List.of();
        }
        List<List<String>> partitions = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += batchSize) {
            partitions.add(new ArrayList<>(ids.subList(i, Math.min(i + batchSize, ids.size()))));
        }
        return partitions;
    }

    public void batchDelete(List<String> ids, String userId, String orgId) {
        requireCustomerService().batchDelete(ids, userId, orgId);
    }

    public void batchUpdate(ResourceBatchEditRequest request, String userId, String orgId) {
        requireCustomerService().batchUpdate(request, userId, orgId);
    }

    private CustomerService requireCustomerService() {
        return Objects.requireNonNull(CommonBeanFactory.getBean(CustomerService.class));
    }

    public CustomerBatchTransferPlan buildBatchTransferPlan(CustomerBatchTransferByConditionRequest request,
                                                            List<Customer> originCustomers, String userId, String orgId) {
        if (CollectionUtils.isEmpty(originCustomers)) {
            return CustomerBatchTransferPlan.empty();
        }
        List<String> owners = originCustomers.stream().map(Customer::getOwner).distinct().toList();
        List<String> ownerUserIds = request.getOwnerUserIds();
        if (CollectionUtils.isEmpty(ownerUserIds)) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        dataScopeService.checkDataPermission(userId, orgId, owners, PermissionConstants.CUSTOMER_MANAGEMENT_TRANSFER);

        Set<String> targetOwnerSet = new HashSet<>(ownerUserIds);
        List<Customer> candidateCustomers = originCustomers.stream()
                .filter(customer -> customer.getOwner() == null || !targetOwnerSet.contains(customer.getOwner()))
                .toList();
        if (candidateCustomers.isEmpty()) {
            return new CustomerBatchTransferPlan(new LinkedHashMap<>(), originCustomers.size());
        }

        Map<String, Integer> userCapacitiesMap = new HashMap<>();
        Map<String, List<Customer>> ownerSnapshotMap = new HashMap<>();
        for (String targetUserId : ownerUserIds) {
            userCapacitiesMap.put(targetUserId, getRemainingTransferCapacity(targetUserId, orgId));
            ownerSnapshotMap.put(targetUserId, customerMobileRuleService.listOwnerOwnedCustomers(targetUserId, orgId, null));
        }

        Map<String, List<String>> ownerToCustomerIds = new LinkedHashMap<>();
        int userIdx = 0;
        int userCount = ownerUserIds.size();
        int skipped = 0;

        for (Customer customer : candidateCustomers) {
            int attempts = 0;
            boolean success = false;
            while (attempts < userCount) {
                String targetUserId = ownerUserIds.get(userIdx);
                Integer capacity = userCapacitiesMap.get(targetUserId);
                List<Customer> ownerSnapshot = ownerSnapshotMap.get(targetUserId);
                if (capacity != null && capacity > 0
                        && !customerMobileRuleService.hasOwnerReceiveConflict(customer.getMobile(), ownerSnapshot, customer.getCreateSource())) {
                    ownerToCustomerIds.computeIfAbsent(targetUserId, key -> new ArrayList<>()).add(customer.getId());
                    userCapacitiesMap.put(targetUserId, capacity - 1);
                    ownerSnapshot.add(customer);
                    success = true;
                    userIdx = (userIdx + 1) % userCount;
                    break;
                }
                userIdx = (userIdx + 1) % userCount;
                attempts++;
            }
            if (!success) {
                skipped++;
            }
        }
        int alreadyOwned = originCustomers.size() - candidateCustomers.size();
        return new CustomerBatchTransferPlan(ownerToCustomerIds, skipped + alreadyOwned);
    }

    public void executeCustomerBatchUpdate(ResourceBatchEditRequest request, List<Customer> originCustomers,
                                           BaseField field, String userId, String orgId) {
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_OWNER.getBusinessKey())) {
            batchUpdate(request, userId, orgId);
            return;
        }
        if (Strings.CS.equals(field.getBusinessKey(), BusinessModuleField.CUSTOMER_MOBILE.getBusinessKey())) {
            batchUpdate(request, userId, orgId);
            return;
        }
        batchUpdate(request, userId, orgId);
    }

    private int getRemainingTransferCapacity(String targetUserId, String orgId) {
        CustomerCapacity customerCapacity = poolCustomerService.getUserCapacity(targetUserId, orgId);
        if (customerCapacity == null || customerCapacity.getCapacity() == null) {
            return Integer.MAX_VALUE;
        }
        List<String> excludeStageIds = new ArrayList<>();
        String paymentStageId = customerStageService.getPaymentStageId(orgId);
        String failStageId = customerStageService.getFailStageId(orgId);
        if (StringUtils.isNotEmpty(paymentStageId)) {
            excludeStageIds.add(paymentStageId);
        }
        if (StringUtils.isNotEmpty(failStageId)) {
            excludeStageIds.add(failStageId);
        }
        int excludeCount = 0;
        if (CollectionUtils.isNotEmpty(excludeStageIds)) {
            excludeCount = extCustomerMapper.countByOwnerAndStages(targetUserId, excludeStageIds);
        }
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.eq(Customer::getOwner, targetUserId).eq(Customer::getInSharedPool, false);
        int ownCount = customerMapper.selectListByLambda(customerWrapper).size();
        return Math.max(0, customerCapacity.getCapacity() - (ownCount - excludeCount));
    }
}
