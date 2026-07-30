package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.domain.CustomerPrivateAutoDeleteConfig;
import cn.cordys.crm.customer.dto.response.CustomerAutoDeleteConfigResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerPrivateAutoDeleteConfigService {

    @Resource
    private BaseMapper<CustomerPrivateAutoDeleteConfig> customerPrivateAutoDeleteConfigMapper;

    public CustomerAutoDeleteConfigResponse getConfig(String orgId) {
        CustomerPrivateAutoDeleteConfig config = getRawConfig(orgId);
        return config == null ? null : new CustomerAutoDeleteConfigResponse(config.getDays());
    }

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE, operator = "{#userId}")
    public void saveConfig(Integer days, String userId, String orgId) {
        CustomerPrivateAutoDeleteConfig config = getRawConfig(orgId);
        long now = System.currentTimeMillis();
        if (config == null) {
            config = new CustomerPrivateAutoDeleteConfig();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(orgId);
            config.setCreateTime(now);
            config.setCreateUser(userId);
            config.setDays(days);
            config.setUpdateTime(now);
            config.setUpdateUser(userId);
            customerPrivateAutoDeleteConfigMapper.insert(config);
            return;
        }
        config.setDays(days);
        config.setUpdateTime(now);
        config.setUpdateUser(userId);
        customerPrivateAutoDeleteConfigMapper.updateById(config);
    }

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.DELETE, operator = "{#userId}")
    public void deleteConfig(String orgId, String userId) {
        LambdaQueryWrapper<CustomerPrivateAutoDeleteConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerPrivateAutoDeleteConfig::getOrganizationId, orgId);
        customerPrivateAutoDeleteConfigMapper.deleteByLambda(wrapper);
    }

    public CustomerPrivateAutoDeleteConfig getRawConfig(String orgId) {
        LambdaQueryWrapper<CustomerPrivateAutoDeleteConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerPrivateAutoDeleteConfig::getOrganizationId, orgId);
        return customerPrivateAutoDeleteConfigMapper.selectListByLambda(wrapper).stream().findFirst().orElse(null);
    }

    public CustomerPrivateAutoDeleteConfig getRawConfig() {
        return customerPrivateAutoDeleteConfigMapper.select(new CustomerPrivateAutoDeleteConfig()).stream()
                .findFirst()
                .orElse(null);
    }
}
