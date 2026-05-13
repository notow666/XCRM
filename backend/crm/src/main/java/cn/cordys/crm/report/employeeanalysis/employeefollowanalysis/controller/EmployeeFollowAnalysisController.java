package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.controller;

import cn.cordys.common.pager.Pager;
import cn.cordys.context.OrganizationContext;
import cn.cordys.security.SessionUtils;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisRebuildRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisSummaryRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisSummaryItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisDrilldownService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisFactBuildService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "员工跟进分析报表")
@Validated
@RestController
@RequestMapping("/report/employee-analysis/follow-analysis")
public class EmployeeFollowAnalysisController {

    @Resource
    private EmployeeFollowAnalysisService employeeFollowAnalysisService;
    @Resource
    private EmployeeFollowAnalysisDrilldownService employeeFollowAnalysisDrilldownService;
    @Resource
    private EmployeeFollowAnalysisFactBuildService employeeFollowAnalysisFactBuildService;

    @PostMapping("/summary")
    @Operation(summary = "员工跟进分析汇总")
    public List<EmployeeFollowAnalysisSummaryItemResponse> summary(@Valid @RequestBody EmployeeFollowAnalysisSummaryRequest request) {
        return employeeFollowAnalysisService.summary(request, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/drilldown")
    @Operation(summary = "员工跟进分析下钻明细")
    public Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldown(@Valid @RequestBody EmployeeFollowAnalysisDrilldownRequest request) {
        return employeeFollowAnalysisDrilldownService.drilldown(request, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/rebuild")
    @Operation(summary = "员工跟进分析历史事实重算")
    public void rebuild(@Valid @RequestBody EmployeeFollowAnalysisRebuildRequest request) {
        employeeFollowAnalysisFactBuildService.rebuildRange(request, SessionUtils.getUserId());
    }
}
