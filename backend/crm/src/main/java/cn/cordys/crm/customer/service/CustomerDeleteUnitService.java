package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.service.LogService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 唯一的客户删除事务单元：一个客户一个 REQUIRES_NEW 短事务。
 */
@Service
public class CustomerDeleteUnitService {

    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private CustomerResourceDeleteService customerResourceDeleteService;
    @Resource
    private LogService logService;

    /**
     * CASCADE 前置校验。先释放客户锁，再由编排器逐合同执行短事务，避免持锁跨越全部合同。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void validateOne(String customerId, String orgId, CustomerDeleteScene scene, Long cutoffTime) {
        Customer customer = extCustomerMapper.selectForDelete(customerId, orgId);
        if (customer == null) {
            throw new GenericException("客户不存在或已被删除");
        }
        validateAutoDeleteCandidate(customer, scene, cutoffTime);
        customerResourceDeleteService.checkResourceRef(customerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Customer deleteOne(String customerId, String orgId, String operator,
                              String customerLogModule, String reason, CustomerDeleteScene scene,
                              Long cutoffTime) {
        Customer customer = extCustomerMapper.selectForDelete(customerId, orgId);
        if (customer == null) {
            throw new GenericException("客户不存在或已被删除");
        }
        validateAutoDeleteCandidate(customer, scene, cutoffTime);
        customerResourceDeleteService.checkResourceRef(customerId);

        List<LogDTO> logs = new ArrayList<>();
        // CASCADE 合同已由无事务编排器逐合同短事务处理；此处只删除客户原有资源。
        customerResourceDeleteService.deleteOne(customerId);

        LogDTO customerLog = new LogDTO(orgId, customerId, operator, LogType.DELETE,
                customerLogModule, customer.getName());
        customerLog.setDetail(reason);
        logs.add(customerLog);
        logService.batchAddSync(logs);
        return customer;
    }

    /*
     * 历史方案：在单客户事务内级联全部合同，可能扩大事务并占用连接。
     * 当前已改为 CustomerContractCascadeDeleteService 在事务外逐合同调用短事务，故保留以下代码仅供追溯。
     *
    private void cascadeContracts(String customerId, String orgId, String operator,
                                  String reason, List<LogDTO> logs) {
        List<Contract> contracts = contractMapper.selectListByLambda(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getCustomerId, customerId)
                        .eq(Contract::getOrganizationId, orgId))
                .stream()
                .sorted(Comparator.comparing(Contract::getId))
                .toList();
        for (Contract contract : contracts) {
            List<ContractPaymentRecord> records = paymentRecordMapper.selectListByLambda(
                    new LambdaQueryWrapper<ContractPaymentRecord>()
                            .eq(ContractPaymentRecord::getContractId, contract.getId()));
            if (CollectionUtils.isNotEmpty(records)) {
                for (ContractPaymentRecord record : records) {
                    LogDTO paymentLog = new LogDTO(orgId, record.getId(), operator, LogType.DELETE,
                            LogModule.CONTRACT_PAYMENT_RECORD, record.getName());
                    paymentLog.setDetail(reason);
                    logs.add(paymentLog);
                }
            }
            contractTransactionService.delete(contract.getId(), operator, orgId);
            LogDTO contractLog = new LogDTO(orgId, contract.getId(), operator, LogType.DELETE,
                    LogModule.CONTRACT_INDEX, contract.getName());
            contractLog.setDetail(reason);
            logs.add(contractLog);
        }
    }
    */

    private void validateAutoDeleteCandidate(Customer customer, CustomerDeleteScene scene, Long cutoffTime) {
        if (scene == CustomerDeleteScene.MANUAL) {
            return;
        }
        if (cutoffTime == null) {
            throw new GenericException("自动删除截止时间不能为空");
        }
        if (scene == CustomerDeleteScene.POOL_AUTO) {
            if (!Strings.CS.equals(customer.getCreateSource(), CustomerCreateSource.POOL_IMPORT)
                    || customer.getCreateTime() == null || customer.getCreateTime() >= cutoffTime) {
                throw new GenericException("客户已不满足公海导入自动删除条件");
            }
            return;
        }
        long lastActivityTime = Math.max(valueOrZero(customer.getCreateTime()),
                Math.max(valueOrZero(customer.getUpdateTime()), valueOrZero(customer.getFollowTime())));
        boolean privateSource = Strings.CS.equals(customer.getCreateSource(), CustomerCreateSource.MANUAL_CREATE)
                || Strings.CS.equals(customer.getCreateSource(), CustomerCreateSource.PRIVATE_IMPORT);
        if (Boolean.TRUE.equals(customer.getInSharedPool()) || !privateSource || lastActivityTime >= cutoffTime) {
            throw new GenericException("客户已不满足私海自动删除条件");
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
