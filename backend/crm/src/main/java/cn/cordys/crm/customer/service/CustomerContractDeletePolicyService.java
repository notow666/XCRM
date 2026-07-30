package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.constants.CustomerContractDeletePolicyType;
import cn.cordys.crm.customer.domain.CustomerContractDeletePolicy;
import cn.cordys.crm.customer.dto.response.CustomerContractDeletePolicyResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerContractDeletePolicyService {

    @Resource
    private BaseMapper<CustomerContractDeletePolicy> customerContractDeletePolicyMapper;

    public CustomerContractDeletePolicyResponse getConfig(String orgId) {
        CustomerContractDeletePolicy config = getRawConfig(orgId);
        return new CustomerContractDeletePolicyResponse(
                config == null ? CustomerContractDeletePolicyType.CASCADE : config.getPolicy()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE, operator = "{#userId}")
    public void saveConfig(String policy, String orgId, String userId) {
        if (!CustomerContractDeletePolicyType.isValid(policy)) {
            throw new GenericException(Translator.get("common.param.error"));
        }
        CustomerContractDeletePolicy config = getRawConfig(orgId);
        long now = System.currentTimeMillis();
        if (config == null) {
            config = new CustomerContractDeletePolicy();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(orgId);
            config.setCreateTime(now);
            config.setCreateUser(userId);
            config.setPolicy(policy);
            config.setUpdateTime(now);
            config.setUpdateUser(userId);
            customerContractDeletePolicyMapper.insert(config);
            return;
        }
        config.setPolicy(policy);
        config.setUpdateTime(now);
        config.setUpdateUser(userId);
        customerContractDeletePolicyMapper.updateById(config);
    }

    private CustomerContractDeletePolicy getRawConfig(String orgId) {
        LambdaQueryWrapper<CustomerContractDeletePolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerContractDeletePolicy::getOrganizationId, orgId);
        return customerContractDeletePolicyMapper.selectListByLambda(wrapper).stream().findFirst().orElse(null);
    }
}
