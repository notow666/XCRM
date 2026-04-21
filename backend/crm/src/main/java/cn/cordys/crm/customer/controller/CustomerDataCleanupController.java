package cn.cordys.crm.customer.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.domain.CustomerDataCleanupConfig;
import cn.cordys.crm.customer.dto.request.CustomerDataCleanupSaveRequest;
import cn.cordys.crm.customer.service.CustomerDataCleanupService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "客户数据清理设置")
@RestController
@RequestMapping("/customer/dataCleanup")
public class CustomerDataCleanupController {

    @Resource
    private CustomerDataCleanupService customerDataCleanupService;

    @GetMapping("/get")
    @Operation(summary = "获取客户数据清理配置")
    public CustomerDataCleanupConfig getConfig() {
        return customerDataCleanupService.getConfig();
    }

    @PostMapping("/save")
    @Operation(summary = "保存客户数据清理配置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void saveConfig(@Validated @RequestBody CustomerDataCleanupSaveRequest request) {
        customerDataCleanupService.saveConfig(request.getFieldIds(), request.getDays(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除客户数据清理配置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void deleteConfig() {
        customerDataCleanupService.deleteConfig();
    }
}
