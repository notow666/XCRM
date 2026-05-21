package cn.cordys.crm.customer.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.domain.CustomerAutoDeleteConfig;
import cn.cordys.crm.customer.dto.response.CustomerAutoDeleteConfigResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class CustomerAutoDeleteService {

    private static final String SYSTEM_OPERATOR = "system";

    @Resource
    private BaseMapper<CustomerAutoDeleteConfig> customerAutoDeleteConfigMapper;

    @Resource
    private CustomerService customerService;

    public CustomerAutoDeleteConfigResponse getConfig() {
        CustomerAutoDeleteConfig config = getRawConfig();
        return config == null ? null : new CustomerAutoDeleteConfigResponse(config.getDays());
    }

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

    public void deleteConfig() {
        customerAutoDeleteConfigMapper.deleteByLambda(new LambdaQueryWrapper<>());
    }

    public int executeAutoDelete() {
        CustomerAutoDeleteConfig config = getRawConfig();
        if (config == null || config.getDays() == null) {
            log.info("客户定时删除配置为空，跳过执行");
            return 0;
        }

        long cutoffTime = System.currentTimeMillis() - (config.getDays() * 24L * 60 * 60 * 1000);
        log.info("开始执行客户定时删除，days={}, cutoffTime={}", config.getDays(), cutoffTime);
        int deletedCount = customerService.autoDeletePoolImportCustomers(cutoffTime, SYSTEM_OPERATOR);
        log.info("客户定时删除执行完成，deletedCount={}", deletedCount);
        return deletedCount;
    }

    private CustomerAutoDeleteConfig getRawConfig() {
        List<CustomerAutoDeleteConfig> list = customerAutoDeleteConfigMapper.select(new CustomerAutoDeleteConfig());
        return CollectionUtils.isNotEmpty(list) ? list.getFirst() : null;
    }
}
