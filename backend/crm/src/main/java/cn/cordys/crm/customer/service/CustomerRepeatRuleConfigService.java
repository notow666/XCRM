package cn.cordys.crm.customer.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.domain.CustomerRepeatRuleConfig;
import cn.cordys.crm.customer.dto.response.CustomerRepeatRuleConfigResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerRepeatRuleConfigService {

    private static final int DEFAULT_REPEAT_AFTER_DAYS = 5;

    @Resource
    private BaseMapper<CustomerRepeatRuleConfig> customerRepeatRuleConfigMapper;

    public CustomerRepeatRuleConfigResponse getConfig() {
        CustomerRepeatRuleConfig config = getConfigEntity();
        CustomerRepeatRuleConfigResponse response = new CustomerRepeatRuleConfigResponse();
        response.setEnabled(config != null && Boolean.TRUE.equals(config.getEnabled()));
        response.setRepeatAfterDays(config == null || config.getRepeatAfterDays() == null || config.getRepeatAfterDays() <= 0
                ? DEFAULT_REPEAT_AFTER_DAYS
                : config.getRepeatAfterDays());
        return response;
    }

    public CustomerRepeatRuleConfig getConfigEntity() {
        CustomerRepeatRuleConfig config = customerRepeatRuleConfigMapper.selectListByLambda(new LambdaQueryWrapper<>()).stream()
                .findFirst()
                .orElse(null);
        if (config != null) {
            if (config.getRepeatAfterDays() == null || config.getRepeatAfterDays() <= 0) {
                config.setRepeatAfterDays(DEFAULT_REPEAT_AFTER_DAYS);
            }
            if (config.getEnabled() == null) {
                config.setEnabled(Boolean.FALSE);
            }
        }
        return config;
    }

    public void save(Boolean enabled, Integer repeatAfterDays, String userId) {
        CustomerRepeatRuleConfig config = getConfigEntity();
        long now = System.currentTimeMillis();
        if (config == null) {
            config = new CustomerRepeatRuleConfig();
            config.setId(IDGenerator.nextStr());
            config.setEnabled(Boolean.TRUE.equals(enabled));
            config.setRepeatAfterDays(repeatAfterDays == null || repeatAfterDays <= 0 ? DEFAULT_REPEAT_AFTER_DAYS : repeatAfterDays);
            config.setCreateTime(now);
            config.setUpdateTime(now);
            config.setCreateUser(userId);
            config.setUpdateUser(userId);
            customerRepeatRuleConfigMapper.insert(config);
            return;
        }
        config.setEnabled(Boolean.TRUE.equals(enabled));
        config.setRepeatAfterDays(repeatAfterDays == null || repeatAfterDays <= 0 ? DEFAULT_REPEAT_AFTER_DAYS : repeatAfterDays);
        config.setUpdateTime(now);
        config.setUpdateUser(userId);
        customerRepeatRuleConfigMapper.updateById(config);
    }
}
