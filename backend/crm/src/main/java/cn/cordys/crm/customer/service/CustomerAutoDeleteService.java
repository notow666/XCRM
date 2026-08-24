package cn.cordys.crm.customer.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerAutoDeleteConfig;
import cn.cordys.crm.customer.domain.CustomerPrivateAutoDeleteConfig;
import cn.cordys.crm.customer.dto.response.CustomerAutoDeleteConfigResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class CustomerAutoDeleteService {

    private static final String SYSTEM_OPERATOR = "system";
    private static final int DELETE_BATCH_SIZE = 100;
    private static final String POOL_DELETE_REASON = "公海导入客户定时删除";
    private static final String PRIVATE_DELETE_REASON = "私海客户长期未跟进和更新自动删除";

    @Resource
    private BaseMapper<CustomerAutoDeleteConfig> customerAutoDeleteConfigMapper;

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private CustomerPrivateAutoDeleteConfigService customerPrivateAutoDeleteConfigService;
    @Resource
    private CustomerAutoDeleteBatchService customerAutoDeleteBatchService;

    public CustomerAutoDeleteConfigResponse getConfig() {
        CustomerAutoDeleteConfig config = getRawConfig();
        return config == null ? null : new CustomerAutoDeleteConfigResponse(config.getDays());
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(Integer days, String userId, String orgId) {
        CustomerAutoDeleteConfig existingConfig = getRawConfig();
        long now = System.currentTimeMillis();
        if (existingConfig != null) {
            existingConfig.setDays(days);
            existingConfig.setUpdateTime(now);
            existingConfig.setUpdateUser(userId);
            customerAutoDeleteConfigMapper.updateById(existingConfig);
            return;
        }

        CustomerAutoDeleteConfig config = new CustomerAutoDeleteConfig();
        config.setId(IDGenerator.nextStr());
        config.setOrganizationId(orgId);
        config.setDays(days);
        config.setCreateTime(now);
        config.setCreateUser(userId);
        config.setUpdateTime(now);
        config.setUpdateUser(userId);
        customerAutoDeleteConfigMapper.insert(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig() {
        customerAutoDeleteConfigMapper.deleteByLambda(new LambdaQueryWrapper<>());
    }

    public int executeAutoDelete() {
        int deletedCount = 0;
        try {
            deletedCount += executePoolAutoDelete();
        } catch (Exception e) {
            log.error("公海导入客户定时删除执行失败", e);
        }
        try {
            deletedCount += executePrivateAutoDelete();
        } catch (Exception e) {
            log.error("私海客户定时删除执行失败", e);
        }
        return deletedCount;
    }

    private int executePoolAutoDelete() {
        CustomerAutoDeleteConfig config = getRawConfig();
        if (config == null || config.getDays() == null) {
            log.info("公海导入客户定时删除配置为空，跳过执行");
            return 0;
        }
        long cutoffTime = calculateCutoffTime(config.getDays());
        int deletedCount = 0;
        while (true) {
            PageHelper.startPage(1, DELETE_BATCH_SIZE, false);
            LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Customer::getCreateSource, CustomerCreateSource.POOL_IMPORT)
                    .ltT(Customer::getCreateTime, cutoffTime)
                    .orderByAsc(Customer::getCreateTime);
            List<Customer> customers = customerMapper.selectListByLambda(wrapper);
            if (CollectionUtils.isEmpty(customers)) {
                break;
            }
            int currentDeletedCount = customerAutoDeleteBatchService.deletePoolBatch(
                    customers.stream().map(Customer::getId).toList(),
                    config.getOrganizationId(),
                    cutoffTime,
                    SYSTEM_OPERATOR,
                    POOL_DELETE_REASON
            );
            deletedCount += currentDeletedCount;
            if (currentDeletedCount == 0) {
                log.warn("公海导入客户定时删除本批次无成功记录，停止本次任务以避免重复查询");
                break;
            }
        }
        log.info("公海导入客户定时删除完成，days={}, cutoffTime={}, deletedCount={}",
                config.getDays(), cutoffTime, deletedCount);
        return deletedCount;
    }

    private int executePrivateAutoDelete() {
        CustomerPrivateAutoDeleteConfig config = customerPrivateAutoDeleteConfigService.getRawConfig();
        if (config == null || config.getDays() == null) {
            log.info("私海客户定时删除配置为空，跳过执行");
            return 0;
        }
        long cutoffTime = calculateCutoffTime(config.getDays());
        int deletedCount = 0;
        while (true) {
            List<Customer> customers = extCustomerMapper.listPrivateAutoDeleteCandidates(
                    config.getOrganizationId(), cutoffTime, DELETE_BATCH_SIZE, null
            );
            if (CollectionUtils.isEmpty(customers)) {
                break;
            }
            int currentDeletedCount = customerAutoDeleteBatchService.deletePrivateBatch(
                    customers.stream().map(Customer::getId).toList(),
                    config.getOrganizationId(),
                    cutoffTime,
                    SYSTEM_OPERATOR,
                    PRIVATE_DELETE_REASON
            );
            deletedCount += currentDeletedCount;
            if (currentDeletedCount == 0) {
                log.warn("私海客户定时删除本批次无成功记录，停止本次任务以避免重复查询");
                break;
            }
        }
        log.info("私海客户定时删除完成，days={}, cutoffTime={}, deletedCount={}",
                config.getDays(), cutoffTime, deletedCount);
        return deletedCount;
    }

    private long calculateCutoffTime(Integer days) {
        return LocalDate.now()
                .minusDays(days - 1L)
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private CustomerAutoDeleteConfig getRawConfig() {
        List<CustomerAutoDeleteConfig> list = customerAutoDeleteConfigMapper.select(new CustomerAutoDeleteConfig());
        return CollectionUtils.isNotEmpty(list) ? list.getFirst() : null;
    }
}
