package cn.cordys.crm.report.customerconversion.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.report.customerconversion.service.CustomerConversionExportService;
import cn.cordys.crm.report.customerconversion.service.CustomerConversionReportService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "客户转化历史报表")
@RestController
@RequestMapping("/report/employee/customer-conversion")
public class CustomerConversionReportController {

    @Resource
    private CustomerConversionReportService reportService;
    @Resource
    private CustomerConversionExportService exportService;

    @PostMapping("/summary")
    @Operation(summary = "客户转化汇总")
    public List<CustomerConversionReportService.SummaryRow> summary(
            @RequestBody CustomerConversionReportService.QueryRequest request) {
        return reportService.summary(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/detail")
    @Operation(summary = "客户转化明细")
    public CustomerConversionReportService.PageResult<CustomerConversionReportService.DetailRow> detail(
            @RequestBody CustomerConversionReportService.DetailRequest request) {
        return reportService.detail(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/export")
    @Operation(summary = "客户转化同步导出")
    public ResponseEntity<ByteArrayResource> export(
            @RequestBody CustomerConversionReportService.QueryRequest request) {
        return exportService.export(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/detail/export")
    @Operation(summary = "客户转化明细同步导出")
    public ResponseEntity<ByteArrayResource> exportDetail(
            @RequestBody CustomerConversionReportService.DetailRequest request) {
        return exportService.exportDetail(request, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }
}
