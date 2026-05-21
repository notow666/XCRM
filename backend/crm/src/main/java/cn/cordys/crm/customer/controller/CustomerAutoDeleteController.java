package cn.cordys.crm.customer.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.dto.request.CustomerAutoDeleteSaveRequest;
import cn.cordys.crm.customer.dto.response.CustomerAutoDeleteConfigResponse;
import cn.cordys.crm.customer.service.CustomerAutoDeleteService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "客户定时删除设置")
@RestController
@RequestMapping("/customer/autoDelete")
public class CustomerAutoDeleteController {

    @Resource
    private CustomerAutoDeleteService customerAutoDeleteService;

    @GetMapping("/get")
    @Operation(summary = "获取客户定时删除配置")
    public CustomerAutoDeleteConfigResponse getConfig() {
        return customerAutoDeleteService.getConfig();
    }

    @PostMapping("/save")
    @Operation(summary = "保存客户定时删除配置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void saveConfig(@Validated @RequestBody CustomerAutoDeleteSaveRequest request) {
        customerAutoDeleteService.saveConfig(request.getDays(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除客户定时删除配置")
    @RequiresPermissions(value = {PermissionConstants.MODULE_SETTING_UPDATE})
    public void deleteConfig() {
        customerAutoDeleteService.deleteConfig();
    }
}
