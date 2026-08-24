package cn.cordys.crm.customer.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.customer.constants.CustomerResultCode;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户原有资源删除能力。事务边界由 CustomerDeleteUnitService 统一控制。
 */
@Service
public class CustomerResourceDeleteService {

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private CustomerContactService customerContactService;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private CustomerCollaborationService customerCollaborationService;
    @Resource
    private CustomerOwnerHistoryService customerOwnerHistoryService;
    @Resource
    private CustomerRelationService customerRelationService;
    @Resource
    private cn.cordys.crm.follow.service.FollowUpRecordService followUpRecordService;
    @Resource
    private cn.cordys.crm.follow.service.FollowUpPlanService followUpPlanService;

    public void checkResourceRef(String customerId) {
        if (extCustomerMapper.hasRefOpportunity(List.of(customerId))) {
            throw new GenericException(CustomerResultCode.CUSTOMER_RESOURCE_REF);
        }
    }

    public void deleteOne(String customerId) {
        List<String> ids = List.of(customerId);
        customerContactService.deleteByCustomerIds(ids);
        customerMapper.deleteByIds(ids);
        customerFieldService.deleteByResourceIds(ids);
        customerCollaborationService.deleteByCustomerIds(ids);
        customerOwnerHistoryService.deleteByCustomerIds(ids);
        customerRelationService.deleteByCustomerIds(ids);
        followUpRecordService.deleteByCustomerIds(ids);
        followUpPlanService.deleteByCustomerIds(ids);
    }
}
