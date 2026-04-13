package cn.cordys.crm.customer.service;

import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.domain.CustomerFailReasonConfig;
import cn.cordys.crm.customer.dto.request.CustomerFailReasonAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerFailReasonUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerFailReasonResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerFailReasonService {

    private final ExtCustomerConfigMapper extCustomerConfigMapper;

    public List<CustomerFailReasonResponse> getList(String orgId) {
        List<CustomerFailReasonConfig> list = extCustomerConfigMapper.getFailReasonList(orgId);
        return list.stream().map(config -> {
            CustomerFailReasonResponse response = new CustomerFailReasonResponse();
            response.setId(config.getId());
            response.setName(config.getName());
            response.setPos(config.getPos());
            return response;
        }).toList();
    }

    public List<OptionDTO> getOptionList(String orgId) {
        List<CustomerFailReasonConfig> list = extCustomerConfigMapper.getFailReasonList(orgId);
        return list.stream().map(config -> {
            OptionDTO option = new OptionDTO();
            option.setId(config.getId());
            option.setName(config.getName());
            return option;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(CustomerFailReasonAddRequest request, String userId, String orgId) {
        List<CustomerFailReasonConfig> existingList = extCustomerConfigMapper.getFailReasonList(orgId);
        long maxPos = existingList.stream()
                .mapToLong(CustomerFailReasonConfig::getPos)
                .max()
                .orElse(0);

        CustomerFailReasonConfig config = new CustomerFailReasonConfig();
        config.setId(IDGenerator.nextStr());
        config.setName(request.getName());
        config.setPos(maxPos + 1);
        config.setOrganizationId(orgId);
        config.setCreateTime(System.currentTimeMillis());
        config.setUpdateTime(System.currentTimeMillis());
        config.setCreateUser(userId);
        config.setUpdateUser(userId);

        extCustomerConfigMapper.insertFailReason(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerFailReasonUpdateRequest request, String userId, String orgId) {
        CustomerFailReasonConfig config = new CustomerFailReasonConfig();
        config.setId(request.getId());
        config.setName(request.getName());
        config.setUpdateTime(System.currentTimeMillis());
        config.setUpdateUser(userId);
        config.setOrganizationId(orgId);

        extCustomerConfigMapper.updateFailReason(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String orgId) {
        extCustomerConfigMapper.deleteFailReason(id, orgId);
    }
}
