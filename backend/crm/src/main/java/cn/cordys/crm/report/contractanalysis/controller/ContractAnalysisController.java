package cn.cordys.crm.report.contractanalysis.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.report.contractanalysis.service.ContractAnalysisExportService;
import cn.cordys.crm.report.contractanalysis.service.ContractAnalysisService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "合同成交分析")
@RestController
@RequestMapping("/report/contract-analysis")
public class ContractAnalysisController {

    @Resource
    private ContractAnalysisService analysisService;
    @Resource
    private ContractAnalysisExportService exportService;

    @PostMapping("/summary")
    @Operation(summary = "合同成交分析汇总")
    @RequiresPermissions(PermissionConstants.CONTRACT_READ)
    public List<ContractAnalysisService.SummaryRow> summary(
            @RequestBody ContractAnalysisService.QueryRequest request) {
        return analysisService.summary(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/detail")
    @Operation(summary = "合同成交分析明细")
    @RequiresPermissions(PermissionConstants.CONTRACT_READ)
    public ContractAnalysisService.PageResult<ContractAnalysisService.DetailRow> detail(
            @RequestBody ContractAnalysisService.DetailRequest request) {
        return analysisService.detail(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/export")
    @Operation(summary = "合同成交分析同步导出")
    @RequiresPermissions(PermissionConstants.CONTRACT_READ)
    public ResponseEntity<ByteArrayResource> export(
            @RequestBody ContractAnalysisService.QueryRequest request) {
        return exportService.export(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/detail/export")
    @Operation(summary = "合同成交分析明细同步导出")
    @RequiresPermissions(PermissionConstants.CONTRACT_READ)
    public ResponseEntity<ByteArrayResource> exportDetail(
            @RequestBody ContractAnalysisService.DetailRequest request) {
        return exportService.exportDetail(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }
}
