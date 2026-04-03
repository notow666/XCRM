//package cn.cordys.tenant.controller;
//
//import cn.cordys.aspectj.context.OperationLogContext;
//import cn.cordys.aspectj.dto.LogContextInfo;
//import cn.cordys.common.constants.InternalUser;
//import cn.cordys.common.exception.GenericException;
//import cn.cordys.common.response.result.CrmHttpResultCode;
//import cn.cordys.tenant.service.TenantProvisioningService;
//import cn.cordys.security.SessionUtils;
//import cn.cordys.tenant.dto.request.TenantProvisionRequest;
//import cn.cordys.tenant.dto.response.TenantProvisionResponse;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.annotation.Resource;
//import jakarta.validation.Valid;
//import org.apache.commons.lang3.Strings;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.validation.annotation.Validated;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * 平台级租户管理（仅内置管理员），用于动态开通租户库并完成迁移。
// */
//@RestController
//@RequestMapping("/platform/tenant")
//@Tag(name = "平台-租户")
//@Validated
//public class TenantPlatformController {
//
//    @Resource
//    private TenantProvisioningService tenantProvisioningService;
//
//    @PostMapping("/provision")
//    @Operation(summary = "开通租户（建库、迁移、注册数据源）")
//    public TenantProvisionResponse provision(@Valid @RequestBody TenantProvisionRequest request) {
//        assertPlatformAdmin();
//        String tenantId = StringUtils.trimToEmpty(request.getCode()).toLowerCase();
//        TenantProvisionResponse response = tenantProvisioningService.provision(request.getCode(), request.getName(),
//                SessionUtils.getUserId(), request.getInitialUserIds());
//        OperationLogContext.setContext(LogContextInfo.builder()
//                .tenantId(tenantId)
//                .resourceId(response.getTenantId())
//                .resourceName(response.getTenantId())
//                .build());
//        return response;
//    }
//
//    private void assertPlatformAdmin() {
//        String userId = SessionUtils.getUserId();
//        if (!Strings.CS.equals(userId, InternalUser.ADMIN.getValue())) {
//            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
//        }
//    }
//}
