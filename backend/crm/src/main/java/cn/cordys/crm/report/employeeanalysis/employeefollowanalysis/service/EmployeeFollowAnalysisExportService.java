package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.service.BaseExportService;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisSummaryRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisSummaryItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisDimensionType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.system.constants.ExportConstants;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.crm.system.excel.handler.CustomHeadColWidthStyleStrategy;
import cn.cordys.crm.system.service.ExportTaskService;
import cn.cordys.registry.ExportThreadRegistry;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.ExcelWriter;
import cn.idev.excel.support.ExcelTypeEnum;
import cn.idev.excel.write.metadata.WriteSheet;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(rollbackFor = Exception.class)
public class EmployeeFollowAnalysisExportService extends BaseExportService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SHEET_NAME = "导出数据";

    @Resource
    private EmployeeFollowAnalysisService employeeFollowAnalysisService;
    @Resource
    private ExportTaskService exportTaskService;

    public String export(EmployeeFollowAnalysisSummaryRequest request, String orgId, String userId, Locale locale) {
        String fileName = buildFileName(request);
        exportTaskService.checkUserTaskLimit(userId, ExportConstants.ExportStatus.PREPARED.name());

        String fileId = IDGenerator.nextStr();
        ExportTask exportTask = exportTaskService.saveTask(
                orgId,
                fileId,
                userId,
                ExportConstants.ExportType.EMPLOYEE_FOLLOW_ANALYSIS.name(),
                fileName
        );

        runExport(orgId, userId, LogModule.REPORT, locale, exportTask, fileName,
                () -> exportData(fileId, exportTask, request, orgId, userId));

        return exportTask.getId();
    }

    private void exportData(String fileId,
                            ExportTask exportTask,
                            EmployeeFollowAnalysisSummaryRequest request,
                            String orgId,
                            String userId) throws InterruptedException {
        if (ExportThreadRegistry.isInterrupted(exportTask.getId())) {
            throw new InterruptedException("线程已被中断，主动退出");
        }
        List<EmployeeFollowAnalysisSummaryItemResponse> summaryRows = employeeFollowAnalysisService.summary(request, orgId, userId);
        if (ExportThreadRegistry.isInterrupted(exportTask.getId())) {
            throw new InterruptedException("线程已被中断，主动退出");
        }

        File file = prepareExportFile(fileId, exportTask.getFileName(), exportTask.getOrganizationId());
        try (ExcelWriter writer = EasyExcel.write(file)
                .head(buildHeadList(request.getDimensionType()))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet(SHEET_NAME).build();
            writer.write(buildDataRows(summaryRows), sheet);
        }
    }

    private List<List<Object>> buildDataRows(List<EmployeeFollowAnalysisSummaryItemResponse> summaryRows) {
        return summaryRows.stream()
                .map(row -> Arrays.<Object>asList(
                        row.getDimensionLabel(),
                        row.getInboundCustomerCount(),
                        row.getContactedCustomerCount(),
                        row.getNewWechatFriendCount(),
                        row.getDialCount(),
                        row.getConnectedCount(),
                        row.getCallOver1MinCount(),
                        row.getCallOver3MinCount(),
                        formatDuration(row.getCallDurationSec()),
                        formatDuration(row.getAvgCallDurationSec())
                ))
                .toList();
    }

    private List<List<String>> buildHeadList(String dimensionTypeValue) {
        return List.of(
                List.of(buildDimensionHeadText(dimensionTypeValue)),
                List.of("入库客户数"),
                List.of("联系客户数"),
                List.of("新增微信好友数"),
                List.of("拨打电话数"),
                List.of("拨打接通数"),
                List.of("一分钟以上通话数"),
                List.of("三分钟以上通话数"),
                List.of("通话时长"),
                List.of("平均通话时长")
        );
    }

    private String buildFileName(EmployeeFollowAnalysisSummaryRequest request) {
        String timeText = buildTimeText(request);
        String dimensionText = buildDimensionText(request.getDimensionType());
        String timestamp = LocalDateTime.now().format(FILE_TIME_FORMATTER);
        return "员工跟进分析报表_" + timeText + "_" + dimensionText + "_" + timestamp;
    }

    private String buildTimeText(EmployeeFollowAnalysisSummaryRequest request) {
        EmployeeFollowAnalysisTimePreset timePreset = EmployeeFollowAnalysisTimePreset.fromValue(request.getTimePreset());
        return switch (timePreset) {
            case TODAY -> "今日";
            case YESTERDAY -> "昨日";
            case WEEK -> "本周";
            case MONTH -> "本月";
            case CUSTOM -> "自定义" + toLocalDate(request.getStartTime()).format(DATE_FORMATTER)
                    + "-" + toLocalDate(request.getEndTime()).format(DATE_FORMATTER);
        };
    }

    private String buildDimensionText(String dimensionTypeValue) {
        EmployeeFollowAnalysisDimensionType dimensionType = EmployeeFollowAnalysisDimensionType.fromValue(dimensionTypeValue);
        return switch (dimensionType) {
            case EMPLOYEE_NAME -> "按员工";
            case EMPLOYEE_DEPT -> "按部门";
            case CUSTOMER_SOURCE -> "按客户来源";
            case STAT_DAY -> "按日期";
            case STAT_MONTH -> "按月份";
        };
    }

    private String buildDimensionHeadText(String dimensionTypeValue) {
        EmployeeFollowAnalysisDimensionType dimensionType = EmployeeFollowAnalysisDimensionType.fromValue(dimensionTypeValue);
        return switch (dimensionType) {
            case EMPLOYEE_NAME -> "员工";
            case EMPLOYEE_DEPT -> "部门";
            case CUSTOMER_SOURCE -> "客户来源";
            case STAT_DAY -> "日期";
            case STAT_MONTH -> "月份";
        };
    }

    private LocalDate toLocalDate(Long timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("custom range timestamp is required");
        }
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String formatDuration(Long seconds) {
        long value = seconds == null ? 0L : Math.max(0L, seconds);
        long hours = value / 3600L;
        long minutes = value % 3600L / 60L;
        long remainSeconds = value % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, remainSeconds);
    }
}
