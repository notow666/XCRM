package cn.cordys.dataspecialist.controller;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminDetailResponse;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminItemResponse;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminPageRequest;
import cn.cordys.dataspecialist.dto.DataSpecialistCreateRequest;
import cn.cordys.dataspecialist.dto.DataSpecialistUpdateRequest;
import cn.cordys.dataspecialist.service.DataSpecialistAdminService;
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
@RequestMapping("/platform/admin/data-specialist")
@Tag(name = "管理中心-数据专员")
@Validated
public class DataSpecialistAdminController {

    @Resource
    private DataSpecialistAdminService dataSpecialistAdminService;

    @PostMapping("/page")
    @Operation(summary = "数据专员分页")
    public Pager<List<DataSpecialistAdminItemResponse>> page(@Valid @RequestBody DataSpecialistAdminPageRequest request) {
        assertPlatformAdmin();
        return dataSpecialistAdminService.page(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "数据专员详情")
    public DataSpecialistAdminDetailResponse detail(@PathVariable("id") String id) {
        assertPlatformAdmin();
        return dataSpecialistAdminService.getDetail(id);
    }

    @PostMapping
    @Operation(summary = "创建数据专员")
    public String create(@Valid @RequestBody DataSpecialistCreateRequest request) {
        String operator = assertPlatformAdmin();
        return dataSpecialistAdminService.create(request, operator);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据专员")
    public void update(@PathVariable("id") String id, @RequestBody DataSpecialistUpdateRequest request) {
        String operator = assertPlatformAdmin();
        dataSpecialistAdminService.update(id, request, operator);
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
