package cn.cordys.crm.customer.service;

import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.customer.domain.CustomerFollowWayConfig;
import cn.cordys.crm.customer.dto.request.CustomerFollowWayAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerFollowWayUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerFollowWayResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerFollowWayService {

    private final ExtCustomerConfigMapper extCustomerConfigMapper;

    public List<CustomerFollowWayResponse> getList(String orgId) {
        List<CustomerFollowWayConfig> list = extCustomerConfigMapper.getFollowWayList(orgId);
        return list.stream().map(config -> {
            CustomerFollowWayResponse response = new CustomerFollowWayResponse();
            response.setId(config.getId());
            response.setName(config.getName());
            response.setPos(config.getPos());
            return response;
        }).toList();
    }

    public List<OptionDTO> getOptionList(String orgId) {
        List<CustomerFollowWayConfig> list = extCustomerConfigMapper.getFollowWayList(orgId);
        return list.stream().map(config -> {
            OptionDTO option = new OptionDTO();
            option.setId(config.getId());
            option.setName(config.getName());
            return option;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(CustomerFollowWayAddRequest request, String userId, String orgId) {
        List<CustomerFollowWayConfig> existingList = extCustomerConfigMapper.getFollowWayList(orgId);
        long maxPos = existingList.stream()
                .mapToLong(CustomerFollowWayConfig::getPos)
                .max()
                .orElse(0);

        CustomerFollowWayConfig config = new CustomerFollowWayConfig();
        config.setId(IDGenerator.nextStr());
        config.setName(request.getName());
        config.setPos(maxPos + 1);
        config.setOrganizationId(orgId);
        config.setCreateTime(System.currentTimeMillis());
        config.setUpdateTime(System.currentTimeMillis());
        config.setCreateUser(userId);
        config.setUpdateUser(userId);

        extCustomerConfigMapper.insertFollowWay(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerFollowWayUpdateRequest request, String userId, String orgId) {
        CustomerFollowWayConfig config = new CustomerFollowWayConfig();
        config.setId(request.getId());
        config.setName(request.getName());
        config.setUpdateTime(System.currentTimeMillis());
        config.setUpdateUser(userId);
        config.setOrganizationId(orgId);

        extCustomerConfigMapper.updateFollowWay(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String orgId) {
        extCustomerConfigMapper.deleteFollowWay(id, orgId);
    }
}
