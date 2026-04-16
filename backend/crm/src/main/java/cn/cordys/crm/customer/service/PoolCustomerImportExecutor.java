package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.domain.BaseResourceSubField;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerContact;
import cn.cordys.crm.customer.domain.CustomerField;
import cn.cordys.crm.customer.domain.CustomerFieldBlob;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.excel.CustomImportAfterDoConsumer;
import cn.cordys.crm.system.excel.listener.CustomFieldImportEventListener;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import cn.idev.excel.FastExcelFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PoolCustomerImportExecutor {

    @Resource
    private ExtCustomerMapper customerMapper;
    @Resource
    private ModuleFormService moduleFormService;
    @Resource
    private BaseMapper<Customer> customerBaseMapper;
    @Resource
    private BaseMapper<CustomerField> customerFieldMapper;
    @Resource
    private BaseMapper<CustomerFieldBlob> customerFieldBlobMapper;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private BaseMapper<CustomerContact> customerContactMapper;

    private static final String OWNER_FIELD_KEY = "customerOwner";
    private static final int BATCH_SIZE = 500;

    @Transactional(rollbackFor = Exception.class)
    public void executeImport(MultipartFile file, String poolId, String userId, String orgId) {
        try (InputStream inputStream = file.getInputStream()) {
            List<BaseField> fields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), orgId);
            List<BaseField> filteredFields = filterOwnerField(fields);
            removeUniqueRules(filteredFields);

            CustomImportAfterDoConsumer<Customer, BaseResourceSubField> afterDo = buildAfterDoConsumer(poolId, userId, orgId);

            CustomFieldImportEventListener<Customer> eventListener = new CustomFieldImportEventListener<>(
                    filteredFields, Customer.class, orgId, userId, "customer_field", afterDo, 2000, null, null);
            FastExcelFactory.read(inputStream, eventListener).headRowNumber(1).ignoreEmptyRow(true).sheet().doRead();

        } catch (Exception e) {
            Throwable cause = e.getCause();
            log.error("pool customer import error", cause != null ? cause : e);
            throw new GenericException(cause != null ? cause.getMessage() : e.getMessage(), cause != null ? cause : e);
        }
    }

    private CustomImportAfterDoConsumer<Customer, BaseResourceSubField> buildAfterDoConsumer(String poolId, String userId, String orgId) {
        return (customers, customerFields, customerFieldBlobs) -> {
            customers.forEach(customer -> {
                customer.setInSharedPool(true);
                customer.setPoolId(poolId);
                customer.setOwner(null);
                customer.setCollectionTime(null);
                customer.setStage(null);
                customer.setStageStatus(null);
            });

            List<String> mobileList = customers.stream()
                    .map(Customer::getMobile)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());

            List<Customer> newCustomers = new ArrayList<>();
            List<Customer> updateCustomers = new ArrayList<>();
            Map<String, String> idMapping = new HashMap<>();

            if (CollectionUtils.isNotEmpty(mobileList)) {
                List<Customer> existingCustomers = customerMapper.getPoolCustomersByMobiles(orgId, poolId, mobileList);
                Map<String, Customer> existingMap = existingCustomers.stream()
                        .collect(Collectors.toMap(Customer::getMobile, c -> c, (a, b) -> a));

                for (Customer customer : customers) {
                    String mobile = customer.getMobile();
                    if (StringUtils.isNotBlank(mobile) && existingMap.containsKey(mobile)) {
                        Customer existing = existingMap.get(mobile);
                        idMapping.put(customer.getId(), existing.getId());
                        customer.setId(existing.getId());
                        customer.setUpdateTime(System.currentTimeMillis());
                        customer.setUpdateUser(userId);
                        updateCustomers.add(customer);
                    } else {
                        newCustomers.add(customer);
                    }
                }
            } else {
                newCustomers.addAll(customers);
            }

            if (!idMapping.isEmpty()) {
                customerFields.forEach(f -> {
                    if (idMapping.containsKey(f.getResourceId())) {
                        f.setResourceId(idMapping.get(f.getResourceId()));
                    }
                });
                customerFieldBlobs.forEach(f -> {
                    if (idMapping.containsKey(f.getResourceId())) {
                        f.setResourceId(idMapping.get(f.getResourceId()));
                    }
                });
            }

            if (CollectionUtils.isNotEmpty(newCustomers)) {
                long currentTime = System.currentTimeMillis();
                newCustomers.forEach(c -> {
                    c.setUpdateUser(userId);
                    c.setUpdateTime(currentTime);
                });
                
                customerBaseMapper.batchInsert(newCustomers);
                
                List<String> newCustomerIds = newCustomers.stream().map(Customer::getId).collect(Collectors.toList());

                List<BaseResourceSubField> newFields = customerFields.stream()
                        .filter(f -> newCustomerIds.contains(f.getResourceId()))
                        .collect(Collectors.toList());
                List<BaseResourceSubField> newBlobFields = customerFieldBlobs.stream()
                        .filter(f -> newCustomerIds.contains(f.getResourceId()))
                        .collect(Collectors.toList());

                if (CollectionUtils.isNotEmpty(newFields)) {
                    customerFieldMapper.batchInsert(newFields.stream()
                            .map(field -> BeanUtils.copyBean(new CustomerField(), field)).collect(Collectors.toList()));
                }
                if (CollectionUtils.isNotEmpty(newBlobFields)) {
                    customerFieldBlobMapper.batchInsert(newBlobFields.stream()
                            .map(field -> BeanUtils.copyBean(new CustomerFieldBlob(), field)).collect(Collectors.toList()));
                }
            }

            List<CustomerContact> contacts = buildContacts(newCustomers, orgId, userId);
            if (CollectionUtils.isNotEmpty(contacts)) {
                customerContactMapper.batchInsert(contacts);
            }

            if (CollectionUtils.isNotEmpty(updateCustomers)) {
                long currentTime = System.currentTimeMillis();
                updateCustomers.forEach(c -> {
                    c.setUpdateUser(userId);
                    c.setUpdateTime(currentTime);
                });
                
                batchMoveToPoolIncludeStage(updateCustomers);

                List<String> updateCustomerIds = updateCustomers.stream().map(Customer::getId).collect(Collectors.toList());
                customerFieldService.deleteByResourceIds(updateCustomerIds);

                List<BaseResourceSubField> updateFields = customerFields.stream()
                        .filter(f -> updateCustomerIds.contains(f.getResourceId()))
                        .collect(Collectors.toList());
                List<BaseResourceSubField> updateBlobFields = customerFieldBlobs.stream()
                        .filter(f -> updateCustomerIds.contains(f.getResourceId()))
                        .collect(Collectors.toList());

                if (CollectionUtils.isNotEmpty(updateFields)) {
                    customerFieldMapper.batchInsert(updateFields.stream()
                            .map(field -> BeanUtils.copyBean(new CustomerField(), field)).collect(Collectors.toList()));
                }
                if (CollectionUtils.isNotEmpty(updateBlobFields)) {
                    customerFieldBlobMapper.batchInsert(updateBlobFields.stream()
                            .map(field -> BeanUtils.copyBean(new CustomerFieldBlob(), field)).collect(Collectors.toList()));
                }
            }
        };
    }

    private void batchMoveToPoolIncludeStage(List<Customer> customers) {
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        for (int i = 0; i < customers.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, customers.size());
            List<Customer> batch = customers.subList(i, end);
            customerMapper.batchMoveToPoolIncludeStage(batch);
        }
    }

    private List<CustomerContact> buildContacts(List<Customer> newCustomers, String orgId, String userId) {
        List<CustomerContact> contacts = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        for (Customer customer : newCustomers) {
            if (StringUtils.isNotBlank(customer.getMobile())) {
                CustomerContact contact = new CustomerContact();
                contact.setId(IDGenerator.nextStr());
                contact.setCustomerId(customer.getId());
                contact.setName(customer.getName());
                contact.setPhone(customer.getMobile());
                contact.setOrganizationId(orgId);
                contact.setCreateTime(currentTime);
                contact.setUpdateTime(currentTime);
                contact.setCreateUser(userId);
                contact.setUpdateUser(userId);
                contact.setOwner(userId);
                contact.setEnable(true);
                contacts.add(contact);
            }
        }
        return contacts;
    }

    private void removeUniqueRules(List<BaseField> fields) {
        for (BaseField field : fields) {
            field.getRules().removeIf(rule -> "unique".equals(rule.getKey()));
        }
    }

    private List<BaseField> filterOwnerField(List<BaseField> fields) {
        return fields.stream()
                .filter(field -> !OWNER_FIELD_KEY.equals(field.getInternalKey()))
                .collect(Collectors.toList());
    }
}