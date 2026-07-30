package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.crm.customer.constants.CustomerCreateSource;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.service.LogService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerSourceConversionBatchService {

    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private LogService logService;

    @Transactional(rollbackFor = Exception.class)
    public int convertNextBatch(String ownerId, String orgId, String operator, int batchSize) {
        List<Customer> customers =
                extCustomerMapper.listConvertibleCreateSourceCustomers(ownerId, orgId, batchSize);
        if (CollectionUtils.isEmpty(customers)) {
            return 0;
        }

        long updateTime = System.currentTimeMillis();
        List<String> ids = customers.stream().map(Customer::getId).toList();
        int updated = extCustomerMapper.batchConvertCreateSourceToPrivate(
                ids, ownerId, orgId, operator, updateTime);
        if (updated != customers.size()) {
            throw new IllegalStateException("客户创建来源批量转换数量不一致");
        }

        List<LogDTO> logs = new ArrayList<>(customers.size());
        for (Customer customer : customers) {
            String targetSource = targetSource(customer.getCreateSource());
            LogDTO logDTO = new LogDTO(
                    orgId,
                    customer.getId(),
                    operator,
                    LogType.UPDATE,
                    LogModule.CUSTOMER_INDEX,
                    customer.getName()
            );
            logDTO.setDetail("创建来源由“" + sourceName(customer.getCreateSource())
                    + "”变更为“" + sourceName(targetSource) + "”");
            logs.add(logDTO);
        }
        logService.batchAddSync(logs);
        return updated;
    }

    private String targetSource(String createSource) {
        if (CustomerCreateSource.POOL_IMPORT.equals(createSource)) {
            return CustomerCreateSource.PRIVATE_IMPORT;
        }
        if (CustomerCreateSource.CLUE_CREATE.equals(createSource)) {
            return CustomerCreateSource.MANUAL_CREATE;
        }
        throw new IllegalArgumentException("不支持转换的客户创建来源");
    }

    private String sourceName(String createSource) {
        return switch (createSource) {
            case CustomerCreateSource.POOL_IMPORT -> "公海导入";
            case CustomerCreateSource.CLUE_CREATE -> "线索池生成";
            case CustomerCreateSource.PRIVATE_IMPORT -> "客户导入";
            case CustomerCreateSource.MANUAL_CREATE -> "自主创建";
            default -> createSource;
        };
    }
}
