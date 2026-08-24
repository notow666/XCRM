package cn.cordys.crm.customer.service;

import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.service.ContractTransactionService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 客户 CASCADE 删除的合同编排器。外层无事务，每份合同使用独立短事务删除。
 */
@Service
public class CustomerContractCascadeDeleteService {

    @Resource
    private BaseMapper<Contract> contractMapper;
    @Resource
    private ContractTransactionService contractTransactionService;

    public void deleteByCustomer(String customerId, String orgId, String operator, String reason) {
        List<Contract> contracts = contractMapper.selectListByLambda(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getCustomerId, customerId)
                        .eq(Contract::getOrganizationId, orgId))
                .stream()
                .sorted(Comparator.comparing(Contract::getId))
                .toList();
        for (Contract contract : contracts) {
            contractTransactionService.deleteForCustomerCascade(contract.getId(), operator, orgId, reason);
        }
    }
}
