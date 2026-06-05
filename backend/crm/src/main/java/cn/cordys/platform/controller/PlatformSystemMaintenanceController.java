package cn.cordys.platform.controller;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.platform.dto.request.PlatformAnnouncementPageRequest;
import cn.cordys.platform.dto.request.PlatformForceLogoutRequest;
import cn.cordys.platform.dto.request.PlatformSystemAnnouncementRequest;
import cn.cordys.platform.dto.response.PlatformSystemAnnouncementItemResponse;
import cn.cordys.platform.dto.response.PlatformSystemMaintenanceStatusResponse;
import cn.cordys.platform.service.PlatformSystemMaintenanceService;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.SessionUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/admin/system-maintenance")
@Tag(name = "管理中心-系统维护")
@Validated
@Slf4j
public class PlatformSystemMaintenanceController {

    @Resource
    private PlatformSystemMaintenanceService platformSystemMaintenanceService;

    @GetMapping("/status")
    @Operation(summary = "系统维护状态")
    public PlatformSystemMaintenanceStatusResponse status() {
        assertPlatformAdmin();
        return platformSystemMaintenanceService.getStatus();
    }

    @PostMapping("/announcement/page")
    @Operation(summary = "分页查询系统公告")
    public Pager<List<PlatformSystemAnnouncementItemResponse>> pageAnnouncements(
            @Valid @RequestBody PlatformAnnouncementPageRequest request) {
        assertPlatformAdmin();
        return platformSystemMaintenanceService.pageAnnouncements(request);
    }

    @PostMapping("/announcement")
    @Operation(summary = "发布系统公告")
    public PlatformSystemAnnouncementItemResponse publishAnnouncement(
            @Valid @RequestBody PlatformSystemAnnouncementRequest request) {
        String operator = assertPlatformAdmin();
        return platformSystemMaintenanceService.publishAnnouncement(
                request.getSubject(), request.getContent(), operator);
    }

    @PostMapping("/force-logout")
    @Operation(summary = "强制全员下线（租户侧与数据专员）")
    public void forceLogout(@Valid @RequestBody(required = false) PlatformForceLogoutRequest request) {
        String operator = assertPlatformAdmin();
        Integer graceSeconds = request == null ? null : request.getGraceSeconds();
        platformSystemMaintenanceService.forceLogoutAll(operator, graceSeconds);
    }

    @PostMapping("/maintenance/enter")
    @Operation(summary = "进入维护排水模式")
    public void enterMaintenance() {
        String operator = assertPlatformAdmin();
        platformSystemMaintenanceService.enterMaintenanceMode(operator);
    }

    @PostMapping("/maintenance/exit")
    @Operation(summary = "退出维护排水模式")
    public void exitMaintenance() {
        String operator = assertPlatformAdmin();
        platformSystemMaintenanceService.exitMaintenanceMode(operator);
    }

    private String assertPlatformAdmin() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name()
                .equalsIgnoreCase(StringUtils.defaultString(user.getSource()))
                || user.getPermissionIds() == null || !user.getPermissionIds().contains("PLATFORM_ADMIN:READ")) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        return user.getId();
    }
}
