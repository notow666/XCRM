package cn.cordys.platform.controller;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.platform.dto.request.PlatformAuditPageRequest;
import cn.cordys.platform.dto.request.PlatformTenantOrgIdUpdateRequest;
import cn.cordys.platform.dto.request.PlatformTenantPageRequest;
import cn.cordys.platform.dto.response.PlatformAuditLogResponse;
import cn.cordys.platform.dto.response.PlatformTenantHealthResponse;
import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import cn.cordys.platform.dto.response.PlatformTenantProvisionTaskResponse;
import cn.cordys.platform.service.PlatformAdminService;
import cn.cordys.platform.service.PlatformTenantProvisionTaskExecutor;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.SessionUser;
import cn.cordys.tenant.dto.request.TenantProvisionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform/admin")
@Tag(name = "管理中心-平台运维")
@Validated
public class PlatformAdminController {

    private static final Logger managementLog = LoggerFactory.getLogger("MANAGEMENT_CENTER_LOG");

    @Resource
    private PlatformAdminService platformAdminService;

    @Resource
    private PlatformTenantProvisionTaskExecutor platformTenantProvisionTaskExecutor;

    @PostMapping("/tenant/page")
    @Operation(summary = "租户分页")
    public Pager<List<PlatformTenantItemResponse>> pageTenants(@Valid @RequestBody PlatformTenantPageRequest request) {
        assertPlatformAdmin();
        return platformAdminService.pageTenants(request);
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "租户详情")
    public PlatformTenantItemResponse tenantDetail(@PathVariable("tenantId") String tenantId) {
        assertPlatformAdmin();
        return platformAdminService.getTenantDetail(tenantId);
    }

    @PostMapping("/tenant/provision")
    @Operation(summary = "创建租户")
    public PlatformTenantProvisionTaskResponse provision(@Valid @RequestBody TenantProvisionRequest request) {
        String operator = assertPlatformAdmin();
        managementLog.info("[MANAGEMENT_CENTER][TENANT_PROVISION_REQUEST] operator={}, tenantCode={}",
                operator, request.getCode());
        PlatformTenantProvisionTaskResponse task = platformAdminService.submitTenantProvisionTask(
                request.getCode(), request.getName(), operator, request.getInitialUserIds(), request.getOrgId()
        );
        if ("PENDING".equals(task.getStatus())) {
            platformTenantProvisionTaskExecutor.executeProvisionTask(
                    task.getTaskId(), task.getTenantId(), request.getName(), operator, request.getInitialUserIds(), request.getOrgId()
            );
        }
        return task;
    }

    @PostMapping("/tenant/{tenantId}/org-id")
    @Operation(summary = "更新租户MMBA部门ID")
    public void updateTenantOrgId(@PathVariable("tenantId") String tenantId,
                                  @Valid @RequestBody PlatformTenantOrgIdUpdateRequest request) {
        String operator = assertPlatformAdmin();
        platformAdminService.updateTenantOrgId(tenantId, request.getOrgId(), operator);
    }

    @GetMapping("/tenant/provision/task/{taskId}")
    @Operation(summary = "租户创建任务详情")
    public PlatformTenantProvisionTaskResponse provisionTask(@PathVariable("taskId") String taskId) {
        assertPlatformAdmin();
        return platformAdminService.getTenantProvisionTask(taskId);
    }

    @PostMapping("/tenant/{tenantId}/status")
    @Operation(summary = "租户启停")
    public void updateStatus(@PathVariable("tenantId") String tenantId,
                             @RequestParam(value = "enabled", required = false) Boolean enabled,
                             @RequestBody(required = false) Map<String, Object> payload) {
        String operator = assertPlatformAdmin();
        Boolean finalEnabled = enabled;
        if (finalEnabled == null && payload != null && payload.get("enabled") != null) {
            Object raw = payload.get("enabled");
            if (raw instanceof Boolean) {
                finalEnabled = (Boolean) raw;
            } else {
                finalEnabled = Boolean.parseBoolean(String.valueOf(raw));
            }
        }
        if (finalEnabled == null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "enabled 参数不能为空");
        }
        platformAdminService.updateTenantStatus(tenantId, finalEnabled, operator);
    }

    @PostMapping("/tenant/{tenantId}/freeze")
    @Operation(summary = "冻结租户")
    public void freeze(@PathVariable("tenantId") String tenantId) {
        String operator = assertPlatformAdmin();
        platformAdminService.updateTenantStatus(tenantId, false, operator);
    }

    @PostMapping("/tenant/{tenantId}/unfreeze")
    @Operation(summary = "解冻租户")
    public void unfreeze(@PathVariable("tenantId") String tenantId) {
        String operator = assertPlatformAdmin();
        platformAdminService.updateTenantStatus(tenantId, true, operator);
    }

    @GetMapping("/tenant/{tenantId}/health")
    @Operation(summary = "租户健康检查")
    public PlatformTenantHealthResponse health(@PathVariable("tenantId") String tenantId) {
        assertPlatformAdmin();
        return platformAdminService.checkTenantHealth(tenantId);
    }

    @PostMapping("/tenant/{tenantId}/migrate")
    @Operation(summary = "重跑租户迁移")
    public void rerunMigrate(@PathVariable("tenantId") String tenantId) {
        String operator = assertPlatformAdmin();
        platformAdminService.rerunTenantMigrate(tenantId, operator);
    }

    @PostMapping("/audit/page")
    @Operation(summary = "平台审计分页")
    public Pager<List<PlatformAuditLogResponse>> pageAudits(@Valid @RequestBody PlatformAuditPageRequest request) {
        assertPlatformAdmin();
        return platformAdminService.pageAuditLogs(request);
    }

    private String assertPlatformAdmin() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !"PLATFORM".equalsIgnoreCase(StringUtils.defaultString(user.getSource()))
                || user.getPermissionIds() == null || !user.getPermissionIds().contains("PLATFORM_ADMIN:READ")) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        return user.getId();
    }
}
