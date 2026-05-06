package cn.cordys.crm.system.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.pager.Pager;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.system.dto.request.WechatAccountStatPageRequest;
import cn.cordys.crm.system.dto.response.WechatAccountStatListResponse;
import cn.cordys.crm.system.service.ContentAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "内容审计")
@Validated
@RestController
@RequestMapping("/content-audit/wechat")
public class ContentAuditController {

    @Resource
    private ContentAuditService contentAuditService;

    @PostMapping("/account-stat/page")
    @RequiresPermissions(PermissionConstants.CONTENT_AUDIT_READ)
    @Operation(summary = "内容审计-微信帐号统计分页")
    public Pager<List<WechatAccountStatListResponse>> listWechatAccountStat(@Valid @RequestBody WechatAccountStatPageRequest request) {
        return contentAuditService.listWechatAccountStat(request, OrganizationContext.getOrganizationId());
    }
}
