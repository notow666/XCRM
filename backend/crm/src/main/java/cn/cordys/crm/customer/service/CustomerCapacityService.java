package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogContextInfo;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.PermissionCache;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerCapacity;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.customer.dto.CustomerCapacityDTO;
import cn.cordys.crm.customer.dto.response.UserCapacityResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerCapacityMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.dto.FilterConditionDTO;
import cn.cordys.crm.system.dto.request.CapacityAddRequest;
import cn.cordys.crm.system.dto.request.CapacityUpdateRequest;
import cn.cordys.crm.system.mapper.ExtUserMapper;
import cn.cordys.crm.system.service.OrganizationUserService;
import cn.cordys.crm.system.service.UserExtendService;
import org.apache.commons.lang3.Strings;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerCapacityService {

    private static final List<String> BATCH_USER_CAPACITY_PERMISSIONS = Arrays.asList(
            PermissionConstants.CUSTOMER_MANAGEMENT_ADD,
            PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
            PermissionConstants.CUSTOMER_MANAGEMENT_TRANSFER,
            PermissionConstants.CUSTOMER_MANAGEMENT_RECYCLE,
            PermissionConstants.CUSTOMER_MANAGEMENT_DELETE,
            PermissionConstants.CUSTOMER_MANAGEMENT_EXPORT
    );

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
    @Resource
    private BaseMapper<CustomerPool> customerPoolMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private PermissionCache permissionCache;

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

    public List<UserCapacityResponse> batchUserCapacityByAssign(String poolId) {
        if(StringUtils.isBlank(poolId)) {

            return Collections.emptyList();
        }
        String orgId = OrganizationContext.getOrganizationId();
        Map<String, String> userNameMap = getPoolScopeUserNameMap(poolId, orgId);

        List<UserCapacityResponse> responses = new ArrayList<>();
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

    private Map<String, String> getAssignUserNameMap(String orgId) {
        List<OptionDTO> authUserOptions = organizationUserService.getUserByAssign(SessionUtils.getUserId(), orgId,
                BATCH_USER_CAPACITY_PERMISSIONS.toArray(new String[0]));
        return authUserOptions.stream()
                .collect(Collectors.toMap(OptionDTO::getId, OptionDTO::getName, (a, b) -> a));
    }

    private Map<String, String> getPoolScopeUserNameMap(String poolId, String orgId) {
        CustomerPool pool = customerPoolMapper.selectByPrimaryKey(poolId);
        if (pool == null || !Strings.CS.equals(pool.getOrganizationId(), orgId)) {
            return Collections.emptyMap();
        }
        List<String> scopeIds = JSON.parseArray(pool.getScopeId(), String.class);
        if (CollectionUtils.isEmpty(scopeIds)) {
            return Collections.emptyMap();
        }
        List<String> scopeUserIds = userExtendService.getScopeOwnerIds(scopeIds, orgId);
        if (CollectionUtils.isEmpty(scopeUserIds)) {
            return Collections.emptyMap();
        }
        List<String> permittedUserIds = scopeUserIds.stream()
                .filter(userId -> hasAnyBatchUserCapacityPermission(userId, orgId))
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(permittedUserIds)) {
            return Collections.emptyMap();
        }
        return extUserMapper.selectUserOptionByIds(permittedUserIds).stream()
                .collect(Collectors.toMap(OptionDTO::getId, OptionDTO::getName, (a, b) -> a));
    }

    private boolean hasAnyBatchUserCapacityPermission(String userId, String orgId) {
        if (Strings.CS.equals(userId, InternalUser.ADMIN.getValue())) {
            return true;
        }
        Set<String> permissionIds = permissionCache.getPermissionIds(userId, orgId);
        return BATCH_USER_CAPACITY_PERMISSIONS.stream().anyMatch(permissionIds::contains);
    }
}