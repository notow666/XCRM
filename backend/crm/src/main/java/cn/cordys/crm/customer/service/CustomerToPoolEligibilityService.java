package cn.cordys.crm.customer.service;

import cn.cordys.crm.contract.constants.ContractStage;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户回公海统一资格判断：先校验创建来源，再校验全部合同阶段。
 */
@Service
public class CustomerToPoolEligibilityService {

    @Resource
    private BaseMapper<Contract> contractMapper;

    public boolean isEligible(Customer customer, String orgId) {
        if (customer == null || Strings.CS.equals(customer.getCreateSource(), CustomerCreateSource.MANUAL_CREATE)
                || Strings.CS.equals(customer.getCreateSource(), CustomerCreateSource.PRIVATE_IMPORT)) {
            return false;
        }
        List<Contract> contracts = contractMapper.selectListByLambda(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getCustomerId, customer.getId())
                .eq(Contract::getOrganizationId, orgId));
        return contracts.stream().allMatch(contract ->
                Strings.CS.equals(contract.getStage(), ContractStage.COMPLETED_PERFORMANCE.name())
                        || Strings.CS.equals(contract.getStage(), ContractStage.VOID.name()));
    }
}
