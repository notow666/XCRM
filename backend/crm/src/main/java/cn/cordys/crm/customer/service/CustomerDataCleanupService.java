package cn.cordys.crm.customer.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerDataCleanupConfig;
import cn.cordys.crm.customer.mapper.ExtCustomerDataCleanupMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class CustomerDataCleanupService {

    @Resource
    private BaseMapper<CustomerDataCleanupConfig> customerDataCleanupConfigMapper;

    @Resource
    private BaseMapper<Customer> customerMapper;

    @Resource
    private ExtCustomerDataCleanupMapper extCustomerDataCleanupMapper;

    public CustomerDataCleanupConfig getConfig() {
        List<CustomerDataCleanupConfig> list = customerDataCleanupConfigMapper.select(new CustomerDataCleanupConfig());
        return CollectionUtils.isNotEmpty(list) ? list.getFirst() : null;
    }

    public void saveConfig(List<String> fieldIds, Integer days, String userId, String orgId) {
        CustomerDataCleanupConfig existingConfig = getConfig();
        if (existingConfig != null) {
            existingConfig.setFieldIds(JSON.toJSONString(fieldIds));
            existingConfig.setDays(days);
            existingConfig.setUpdateTime(System.currentTimeMillis());
            existingConfig.setUpdateUser(userId);
            customerDataCleanupConfigMapper.updateById(existingConfig);
        } else {
            CustomerDataCleanupConfig config = new CustomerDataCleanupConfig();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(orgId);
            config.setFieldIds(JSON.toJSONString(fieldIds));
            config.setDays(days);
            config.setCreateTime(System.currentTimeMillis());
            config.setCreateUser(userId);
            config.setUpdateTime(System.currentTimeMillis());
            config.setUpdateUser(userId);
            customerDataCleanupConfigMapper.insert(config);
        }
    }

    public void deleteConfig() {
        customerDataCleanupConfigMapper.deleteByLambda(new LambdaQueryWrapper<>());
    }

    public void executeCleanup() {
        CustomerDataCleanupConfig config = getConfig();
        if (config == null || StringUtils.isBlank(config.getFieldIds()) || config.getDays() == null) {
            log.info("客户数据清理配置为空，跳过执行");
            return;
        }

        List<String> fieldIds = JSON.parseArray(config.getFieldIds(), String.class);
        if (CollectionUtils.isEmpty(fieldIds)) {
            log.info("客户数据清理字段列表为空，跳过执行");
            return;
        }

        long cutoffTime = System.currentTimeMillis() - (config.getDays() * 24L * 60 * 60 * 1000);
        log.info("开始执行客户数据清理，days={}, cutoffTime={}, fieldIds={}", config.getDays(), cutoffTime, fieldIds);

        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.ltT(Customer::getCreateTime, cutoffTime);
        List<Customer> customers = customerMapper.selectListByLambda(wrapper);

        if (CollectionUtils.isEmpty(customers)) {
            log.info("没有满足清理条件的客户");
            return;
        }

        List<String> customerIds = customers.stream().map(Customer::getId).toList();

        List<ExtCustomerDataCleanupMapper.CleanupFieldInfo> fieldInfoList =
                extCustomerDataCleanupMapper.selectCleanupFieldInfoList(customerIds, fieldIds);
        log.info("待清理字段: {}", fieldInfoList.stream()
                .map(info -> info.fieldName() + "(" + info.fieldId() + ")")
                .distinct()
                .toList());

        int fieldCount = extCustomerDataCleanupMapper.clearFieldValueByResourceAndFieldIds(customerIds, fieldIds);
        int blobCount = extCustomerDataCleanupMapper.clearFieldBlobValueByResourceAndFieldIds(customerIds, fieldIds);
        log.info("客户数据清理完成，共清理 {} 条客户数据，customer_field: {}条, customer_field_blob: {}条",
                customers.size(), fieldCount, blobCount);
    }
}