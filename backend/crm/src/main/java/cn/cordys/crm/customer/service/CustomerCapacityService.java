package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogContextInfo;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerCapacity;
import cn.cordys.crm.customer.dto.CustomerCapacityDTO;
import cn.cordys.crm.customer.dto.response.UserCapacityResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerCapacityMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.FilterConditionDTO;
import cn.cordys.crm.system.dto.request.CapacityAddRequest;
import cn.cordys.crm.system.dto.request.CapacityUpdateRequest;
import cn.cordys.crm.system.service.OrganizationUserService;
import cn.cordys.crm.system.service.UserExtendService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerCapacityService {

    @Resource
    private UserExtendService userExtendService;
    @Resource
    private BaseMapper<CustomerCapacity> customerCapacityMapper;
    @Resource
    private ExtCustomerCapacityMapper extCustomerCapacityMapper;
    @Resource
    private OrganizationUserService organizationUserService;
    @Resource
    private CustomerStageService customerStageService;
    @Resource
    private PoolCustomerService poolCustomerService;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    /**
     * 获取客户库容设置
     *
     * @return 客户库容设置集合
     */
    public List<CustomerCapacityDTO> list(String currentOrgId) {
        List<CustomerCapacityDTO> capacityList = new ArrayList<>();
        LambdaQueryWrapper<CustomerCapacity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerCapacity::getOrganizationId, currentOrgId).orderByDesc(CustomerCapacity::getCreateTime);
        List<CustomerCapacity> capacities = customerCapacityMapper.selectListByLambda(wrapper);
        if (CollectionUtils.isEmpty(capacities)) {
            return new ArrayList<>();
        }
        capacities.forEach(capacity -> {
            CustomerCapacityDTO capacityDTO = new CustomerCapacityDTO();
            capacityDTO.setId(capacity.getId());
            capacityDTO.setCapacity(capacity.getCapacity());
            capacityDTO.setMembers(userExtendService.getScope(JSON.parseArray(capacity.getScopeId(), String.class)));
            capacityDTO.setFilters(StringUtils.isEmpty(capacity.getFilter()) ? new ArrayList<>() : JSON.parseArray(capacity.getFilter(), FilterConditionDTO.class));
            capacityList.add(capacityDTO);
        });
        return capacityList;
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.ADD)
    public void add(CapacityAddRequest request, String currentUserId, String currentOrgId) {
        List<CustomerCapacity> oldCapacities = customerCapacityMapper.selectAll(null);
        List<String> targetScopeIds = oldCapacities.stream().flatMap(capacity -> JSON.parseArray(capacity.getScopeId(), String.class).stream())
                .collect(Collectors.toList());
        boolean duplicate = userExtendService.hasDuplicateScopeObj(request.getScopeIds(), targetScopeIds, currentOrgId);
        if (duplicate) {
            throw new GenericException(Translator.get("capacity.scope.duplicate"));
        }

        CustomerCapacity capacity = new CustomerCapacity();
        capacity.setId(IDGenerator.nextStr());
        capacity.setOrganizationId(currentOrgId);
        capacity.setCapacity(request.getCapacity());
        capacity.setScopeId(JSON.toJSONString(request.getScopeIds()));
        capacity.setFilter(CollectionUtils.isNotEmpty(request.getFilters()) ? JSON.toJSONString(request.getFilters()) : null);
        capacity.setCreateTime(System.currentTimeMillis());
        capacity.setCreateUser(currentUserId);
        capacity.setUpdateTime(System.currentTimeMillis());
        capacity.setUpdateUser(currentUserId);
        customerCapacityMapper.insert(capacity);

        // 添加日志上下文
        OperationLogContext.setContext(LogContextInfo.builder()
                .modifiedValue(capacity)
                .resourceId(capacity.getId())
                .resourceName(Translator.get("module.customer.capacity.setting"))
                .build());
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE)
    public void update(CapacityUpdateRequest request, String currentUserId, String currentOrgId) {
        CustomerCapacity oldCapacity = customerCapacityMapper.selectByPrimaryKey(request.getId());
        if (oldCapacity == null) {
            throw new GenericException(Translator.get("capacity.not.exist"));
        }
        LambdaQueryWrapper<CustomerCapacity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerCapacity::getOrganizationId, currentOrgId).nq(CustomerCapacity::getId, request.getId());
        List<CustomerCapacity> oldCapacities = customerCapacityMapper.selectListByLambda(wrapper);
        List<String> targetScopeIds = oldCapacities.stream().flatMap(capacity -> JSON.parseArray(capacity.getScopeId(), String.class).stream())
                .collect(Collectors.toList());
        boolean duplicate = userExtendService.hasDuplicateScopeObj(request.getScopeIds(), targetScopeIds, currentOrgId);
        if (duplicate) {
            throw new GenericException(Translator.get("capacity.scope.duplicate"));
        }
        oldCapacity.setScopeId(JSON.toJSONString(request.getScopeIds()));
        oldCapacity.setCapacity(request.getCapacity());
        oldCapacity.setFilter(CollectionUtils.isNotEmpty(request.getFilters()) ? JSON.toJSONString(request.getFilters()) : null);
        oldCapacity.setUpdateTime(System.currentTimeMillis());
        oldCapacity.setUpdateUser(currentUserId);
        extCustomerCapacityMapper.updateCapacity(oldCapacity);

        OperationLogContext.setContext(
                LogContextInfo.builder()
                        .resourceId(request.getId())
                        .resourceName(Translator.get("module.customer.capacity.setting"))
                        .originalValue(oldCapacity)
                        .modifiedValue(customerCapacityMapper.selectByPrimaryKey(request.getId()))
                        .build()
        );
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.DELETE, resourceId = "{#id}")
    public void delete(String id) {
        customerCapacityMapper.deleteByPrimaryKey(id);
        // 设置操作对象
        OperationLogContext.setResourceName(Translator.get("module.customer.capacity.setting"));
    }

    public List<UserCapacityResponse> batchUserCapacity() {
        List<UserCapacityResponse> responses = new ArrayList<>();
        String orgId = OrganizationContext.getOrganizationId();
        List<OptionDTO> authUserOptions = organizationUserService.getAuthUserOptions(SessionUtils.getUserId(), orgId,
                PermissionConstants.CUSTOMER_MANAGEMENT_ADD,
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                PermissionConstants.CUSTOMER_MANAGEMENT_TRANSFER,
                PermissionConstants.CUSTOMER_MANAGEMENT_RECYCLE,
                PermissionConstants.CUSTOMER_MANAGEMENT_DELETE,
                PermissionConstants.CUSTOMER_MANAGEMENT_EXPORT
                );

        Map<String, String> userNameMap = authUserOptions.stream()
                .collect(Collectors.toMap(OptionDTO::getId, OptionDTO::getName, (a, b) -> a));

        List<String> excludeStageIds = new ArrayList<>();
        String paymentStageId = customerStageService.getPaymentStageId(orgId);
        String failStageId = customerStageService.getFailStageId(orgId);
        if (StringUtils.isNotEmpty(paymentStageId)) {
            excludeStageIds.add(paymentStageId);
        }
        if (StringUtils.isNotEmpty(failStageId)) {
            excludeStageIds.add(failStageId);
        }
        for (String userId : userNameMap.keySet()) {
            UserCapacityResponse response = new UserCapacityResponse();
            response.setUserId(userId);
            response.setUserName(userNameMap.getOrDefault(userId, ""));
            var capacity = poolCustomerService.getUserCapacity(userId, orgId);
            if (capacity != null && capacity.getCapacity() != null) {
                response.setCapacity(capacity.getCapacity());
                LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Customer::getOwner, userId).eq(Customer::getInSharedPool, false);
                int ownCount = customerMapper.selectListByLambda(wrapper).size();
                int excludeCount = 0;
                if (CollectionUtils.isNotEmpty(excludeStageIds)) {
                    excludeCount = extCustomerMapper.countByOwnerAndStages(userId, excludeStageIds);
                }
                response.setOwnedCount(ownCount);
                response.setRemainingCapacity(capacity.getCapacity() - (ownCount - excludeCount));
            }
            responses.add(response);
        }
        return responses.stream()
                .filter(u -> u.getRemainingCapacity() == null || u.getRemainingCapacity() > 0)
                .sorted(new UserCapacityResponse.UserCapacityComparator().reversed()).collect(Collectors.toList());
    }
}