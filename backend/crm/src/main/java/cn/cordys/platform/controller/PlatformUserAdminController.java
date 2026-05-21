package cn.cordys.platform.controller;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.platform.dto.PlatformUserAdminDetailResponse;
import cn.cordys.platform.dto.PlatformUserAdminItemResponse;
import cn.cordys.platform.dto.PlatformUserAdminPageRequest;
import cn.cordys.platform.dto.PlatformUserCreateRequest;
import cn.cordys.platform.dto.PlatformUserUpdateRequest;
import cn.cordys.platform.service.PlatformUserAdminService;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platform/admin/platform-user")
@Tag(name = "管理中心-平台超管")
@Validated
public class PlatformUserAdminController {

    @Resource
    private PlatformUserAdminService platformUserAdminService;

    @PostMapping("/page")
    @Operation(summary = "平台管理员分页")
    public Pager<List<PlatformUserAdminItemResponse>> page(@Valid @RequestBody PlatformUserAdminPageRequest request) {
        assertPlatformAdmin();
        return platformUserAdminService.page(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "平台管理员详情")
    public PlatformUserAdminDetailResponse detail(@PathVariable("id") String id) {
        assertPlatformAdmin();
        return platformUserAdminService.getDetail(id);
    }

    @PostMapping
    @Operation(summary = "创建平台管理员")
    public String create(@Valid @RequestBody PlatformUserCreateRequest request) {
        String operator = assertPlatformAdmin();
        return platformUserAdminService.create(request, operator);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新平台管理员")
    public void update(@PathVariable("id") String id, @RequestBody PlatformUserUpdateRequest request) {
        String operator = assertPlatformAdmin();
        platformUserAdminService.update(id, request, operator);
    }

    private String assertPlatformAdmin() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(StringUtils.defaultString(user.getSource()))
                || user.getPermissionIds() == null || !user.getPermissionIds().contains("PLATFORM_ADMIN:READ")) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        return user.getId();
    }
}
