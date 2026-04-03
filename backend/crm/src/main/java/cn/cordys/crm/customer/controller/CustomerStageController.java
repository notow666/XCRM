package cn.cordys.crm.customer.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.dto.request.CustomerStageAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageRollBackRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerStageConfigResponse;
import cn.cordys.crm.customer.service.CustomerStageService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "客户阶段设置")
@RestController
@RequestMapping("/customer/stage")
public class CustomerStageController {

    @Resource
    private CustomerStageService customerStageService;

    @GetMapping("/get")
    @Operation(summary = "客户阶段配置列表")
    public CustomerStageConfigResponse getStageConfigList() {
        return customerStageService.getStageConfigList(OrganizationContext.getOrganizationId());
    }

    @PostMapping("/add")
    @Operation(summary = "添加客户阶段")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public String add(@RequestBody CustomerStageAddRequest request) {
        return customerStageService.addStageConfig(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "删除客户阶段")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void delete(@PathVariable("id") @Validated String id) {
        customerStageService.delete(id, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/update-rollback")
    @Operation(summary = "客户阶段回退设置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void update(@Validated @RequestBody CustomerStageRollBackRequest request) {
        customerStageService.updateRollBack(request, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/update")
    @Operation(summary = "更新客户阶段配置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void update(@Validated @RequestBody CustomerStageUpdateRequest request) {
        customerStageService.update(request, SessionUtils.getUserId());
    }

    @PostMapping("/sort")
    @Operation(summary = "客户阶段排序")
    @RequiresPermissions(PermissionConstants.MODULE_SETTING_UPDATE)
    public void sort(@RequestBody List<String> ids) {
        customerStageService.sort(ids, OrganizationContext.getOrganizationId());
    }
}