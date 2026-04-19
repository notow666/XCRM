package cn.cordys.crm.customer.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.CustomerCapacityDTO;
import cn.cordys.crm.customer.dto.response.UserCapacityResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.service.CustomerCapacityService;
import cn.cordys.crm.customer.service.CustomerStageService;
import cn.cordys.crm.customer.service.PoolCustomerService;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.request.CapacityAddRequest;
import cn.cordys.crm.system.dto.request.CapacityUpdateRequest;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/account-capacity")
@Tag(name = "客户库容容量设置")
public class CustomerCapacityController {

    @Resource
    private CustomerCapacityService customerCapacityService;
    @Resource
    private PoolCustomerService poolCustomerService;
    @Resource
    private CustomerStageService customerStageService;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<User> userMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;

    @GetMapping("/get")
    @Operation(summary = "获取客户库容设置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public List<CustomerCapacityDTO> list() {
        return customerCapacityService.list(OrganizationContext.getOrganizationId());
    }

    @PostMapping("/add")
    @Operation(summary = "添加客户库容设置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void add(@Validated @RequestBody CapacityAddRequest request) {
        customerCapacityService.add(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/update")
    @Operation(summary = "修改客户库容设置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void update(@Validated @RequestBody CapacityUpdateRequest request) {
        customerCapacityService.update(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "删除客户库容设置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void delete(@PathVariable("id") @Validated String id) {
        customerCapacityService.delete(id);
    }

    @GetMapping("/batch-user-capacity")
    @Operation(summary = "批量获取用户库容信息")
    public List<UserCapacityResponse> batchUserCapacity(@RequestParam("userIds") String userIdsStr) {
        String orgId = OrganizationContext.getOrganizationId();
        List<UserCapacityResponse> responses = new ArrayList<>();
        List<String> userIds = StringUtils.isNotEmpty(userIdsStr) 
            ? Arrays.asList(userIdsStr.split(",")) 
            : new ArrayList<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return responses;
        }
        List<User> users = userMapper.selectByIds(userIds);
        Map<String, String> userNameMap = users.stream().collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        List<String> excludeStageIds = new ArrayList<>();
        String paymentStageId = customerStageService.getPaymentStageId(orgId);
        String failStageId = customerStageService.getFailStageId(orgId);
        if (StringUtils.isNotEmpty(paymentStageId)) {
            excludeStageIds.add(paymentStageId);
        }
        if (StringUtils.isNotEmpty(failStageId)) {
            excludeStageIds.add(failStageId);
        }
        for (String userId : userIds) {
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
        return responses;
    }
}
