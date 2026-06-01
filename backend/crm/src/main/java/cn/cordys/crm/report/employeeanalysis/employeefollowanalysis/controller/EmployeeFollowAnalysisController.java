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
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisExportService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeStatDayBuildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
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
@Slf4j
public class EmployeeFollowAnalysisController {

    @Resource
    private EmployeeFollowAnalysisService employeeFollowAnalysisService;
    @Resource
    private EmployeeFollowAnalysisDrilldownService employeeFollowAnalysisDrilldownService;
    @Resource
    private EmployeeFollowAnalysisExportService employeeFollowAnalysisExportService;
    @Resource
    private EmployeeStatDayBuildService employeeStatDayBuildService;

    @PostMapping("/summary")
    @Operation(summary = "员工跟进分析汇总")
    public List<EmployeeFollowAnalysisSummaryItemResponse> summary(@Valid @RequestBody EmployeeFollowAnalysisSummaryRequest request) {
        long start = System.currentTimeMillis();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = SessionUtils.getUserId();
        log.info("员工跟进分析请求开始, api=summary, orgId={}, userId={}, request={}", orgId, userId, request);
        List<EmployeeFollowAnalysisSummaryItemResponse> result = employeeFollowAnalysisService.summary(request, orgId, userId);
        log.info("员工跟进分析请求结束, api=summary, orgId={}, costMs={}, resultSize={}", orgId, System.currentTimeMillis() - start, result == null ? 0 : result.size());
        return result;
    }

    @PostMapping("/export")
    @Operation(summary = "员工跟进分析汇总导出")
    public String export(@Valid @RequestBody EmployeeFollowAnalysisSummaryRequest request) {
        long start = System.currentTimeMillis();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = SessionUtils.getUserId();
        log.info("员工跟进分析请求开始, api=export, orgId={}, userId={}, request={}", orgId, userId, request);
        String taskId = employeeFollowAnalysisExportService.export(request, orgId, userId, LocaleContextHolder.getLocale());
        log.info("员工跟进分析请求结束, api=export, orgId={}, userId={}, costMs={}, taskId={}", orgId, userId, System.currentTimeMillis() - start, taskId);
        return taskId;
    }

    @PostMapping("/drilldown")
    @Operation(summary = "员工跟进分析下钻明细")
    public Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldown(@Valid @RequestBody EmployeeFollowAnalysisDrilldownRequest request) {
        long start = System.currentTimeMillis();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = SessionUtils.getUserId();
        log.info("员工跟进分析请求开始, api=drilldown, orgId={}, userId={}, request={}", orgId, userId, request);
        Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> result = employeeFollowAnalysisDrilldownService.drilldown(request, orgId, userId);
        int resultSize = result == null || result.getList() == null ? 0 : result.getList().size();
        log.info("员工跟进分析请求结束, api=drilldown, orgId={}, costMs={}, resultSize={}", orgId, System.currentTimeMillis() - start, resultSize);
        return result;
    }

    @PostMapping("/rebuild")
    @Operation(summary = "员工跟进分析日报重算")
    public void rebuild(@Valid @RequestBody EmployeeFollowAnalysisRebuildRequest request) {
        long start = System.currentTimeMillis();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = SessionUtils.getUserId();
        log.info("员工跟进分析请求开始, api=rebuild, orgId={}, operatorUserId={}, request={}", orgId, userId, request);
        employeeStatDayBuildService.rebuildRange(request, userId);
        log.info("员工跟进分析请求结束, api=rebuild, orgId={}, operatorUserId={}, costMs={}", orgId, userId, System.currentTimeMillis() - start);
    }
}
