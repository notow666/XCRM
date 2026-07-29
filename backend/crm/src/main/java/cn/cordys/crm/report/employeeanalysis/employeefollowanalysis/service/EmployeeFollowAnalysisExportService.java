package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisSummaryRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisSummaryItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisDimensionType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.system.excel.handler.CustomHeadColWidthStyleStrategy;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.ExcelWriter;
import cn.idev.excel.support.ExcelTypeEnum;
import cn.idev.excel.write.metadata.WriteSheet;
import jakarta.annotation.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EmployeeFollowAnalysisExportService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SHEET_NAME = "导出数据";
    private static final String DURATION_FORMAT_MINUTES = "minutes";

    @Resource
    private EmployeeFollowAnalysisService employeeFollowAnalysisService;

    public ResponseEntity<ByteArrayResource> export(EmployeeFollowAnalysisSummaryRequest request, String orgId, String userId) {
        String fileName = buildFileName(request);
        List<EmployeeFollowAnalysisSummaryItemResponse> summaryRows = employeeFollowAnalysisService.summary(request, orgId, userId);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(outputStream)
                .head(buildHeadList(request.getDimensionType(), request.getCustomCallDurationSec()))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet(SHEET_NAME).build();
            writer.write(buildDataRows(
                    summaryRows,
                    request.getDurationFormat(),
                    request.getCustomCallDurationSec()
            ), sheet);
        } catch (Exception e) {
            throw new RuntimeException("员工跟进分析报表导出失败", e);
        }

        byte[] bytes = outputStream.toByteArray();
        String downloadFileName = fileName + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + UriUtils.encode(downloadFileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private List<List<Object>> buildDataRows(List<EmployeeFollowAnalysisSummaryItemResponse> summaryRows,
                                             String durationFormat,
                                             Integer customCallDurationSec) {
        return summaryRows.stream()
                .map(row -> buildDataRow(row, durationFormat, customCallDurationSec))
                .toList();
    }

    private List<Object> buildDataRow(EmployeeFollowAnalysisSummaryItemResponse row,
                                      String durationFormat,
                                      Integer customCallDurationSec) {
        List<Object> dataRow = new ArrayList<>();
        dataRow.add(row.getDimensionLabel());
        dataRow.add(row.getInboundCustomerCount());
        dataRow.add(row.getContactedCustomerCount());
        dataRow.add(row.getNewWechatFriendCount());
        dataRow.add(row.getVisitCustomerCount());
        dataRow.add(row.getDialCount());
        dataRow.add(row.getConnectedCount());
        dataRow.add(row.getCallOver1MinCount());
        dataRow.add(row.getCallOver3MinCount());
        if (customCallDurationSec != null) {
            dataRow.add(row.getCustomDurationCallCount());
        }
        dataRow.add(formatDuration(row.getCallDurationSec(), durationFormat));
        dataRow.add(formatDuration(row.getAvgCallDurationSec(), durationFormat));
        return dataRow;
    }

    private List<List<String>> buildHeadList(String dimensionTypeValue, Integer customCallDurationSec) {
        List<List<String>> headList = new ArrayList<>();
        headList.add(List.of(buildDimensionHeadText(dimensionTypeValue)));
        headList.add(List.of("入库客户数"));
        headList.add(List.of("联系客户数"));
        headList.add(List.of("新增微信好友数"));
        headList.add(List.of("上门客户数"));
        headList.add(List.of("拨打电话数"));
        headList.add(List.of("拨打接通数"));
        headList.add(List.of("一分钟以上通话数"));
        headList.add(List.of("三分钟以上通话数"));
        if (customCallDurationSec != null) {
            headList.add(List.of(customCallDurationSec + "秒以上通话数"));
        }
        headList.add(List.of("通话时长"));
        headList.add(List.of("平均通话时长"));
        return headList;
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

    private String formatDuration(Long seconds, String durationFormat) {
        long value = seconds == null ? 0L : Math.max(0L, seconds);
        if (DURATION_FORMAT_MINUTES.equals(durationFormat)) {
            return String.format(Locale.ROOT, "%.1f分钟", value / 60D);
        }
        long hours = value / 3600L;
        long minutes = value % 3600L / 60L;
        long remainSeconds = value % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, remainSeconds);
    }
}
