package cn.cordys.mmba.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.mmba.dto.request.MmbaCallLogCleanConfigSaveRequest;
import cn.cordys.mmba.dto.request.MmbaCallLogCleanRequest;
import cn.cordys.mmba.dto.response.MmbaCallLogCleanConfigResponse;
import cn.cordys.mmba.dto.response.MmbaCallLogCleanResponse;
import cn.cordys.mmba.service.MmbaCallLogCleanService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通话记录清除")
@RestController
@RequestMapping("/call-log-clean")
public class MmbaCallLogCleanController {

    @Resource
    private MmbaCallLogCleanService callLogCleanService;

    @PostMapping("/execute")
    @Operation(summary = "手动清除员工通话记录")
    @RequiresPermissions(PermissionConstants.SYS_ORGANIZATION_USER_CLEAN_CALL_LOG)
    public MmbaCallLogCleanResponse execute(@Validated @RequestBody MmbaCallLogCleanRequest request) {
        return callLogCleanService.executeManual(request.getUserIds(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/config")
    @Operation(summary = "获取定时清除通话记录配置")
    @RequiresPermissions(PermissionConstants.CALL_LOG_CLEAN_CONFIG_UPDATE)
    public MmbaCallLogCleanConfigResponse getConfig() {
        return callLogCleanService.getConfig(OrganizationContext.getOrganizationId());
    }

    @PostMapping("/config")
    @Operation(summary = "保存定时清除通话记录配置")
    @RequiresPermissions(PermissionConstants.CALL_LOG_CLEAN_CONFIG_UPDATE)
    public void saveConfig(@Validated @RequestBody MmbaCallLogCleanConfigSaveRequest request) {
        callLogCleanService.saveConfig(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
