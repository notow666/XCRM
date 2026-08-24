package cn.cordys.crm.report.customerconversion.service;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CustomerConversionExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<String, String> EVENT_LABELS = Map.of(
            "VISIT", "上门客户",
            "CONTRACT_SIGNED", "签约客户",
            "PAYMENT_APPROVED", "回款客户");

    @Resource
    private CustomerConversionReportService reportService;

    public ResponseEntity<ByteArrayResource> export(CustomerConversionReportService.QueryRequest request,
                                                    String orgId, String userId) {
        List<CustomerConversionReportService.SummaryRow> rows = reportService.summary(request, orgId, userId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(output)
                .head(List.of(List.of("统计维度"), List.of("上门客户数"), List.of("签约客户数"),
                        List.of("回款客户数"), List.of("到店签单率"), List.of("到店转化率")))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet("客户转化").build();
            writer.write(rows.stream().map(row -> List.of(row.getDimensionLabel(), row.getVisitCustomerCount(),
                    row.getSignedCustomerCount(), row.getPaymentCustomerCount(), row.getSignRate(),
                    row.getPaymentRate())).toList(), sheet);
        }
        byte[] bytes = output.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + UriUtils.encode("客户转化报表.xlsx", StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    public ResponseEntity<ByteArrayResource> exportDetail(CustomerConversionReportService.DetailRequest request,
                                                          String orgId, String userId) {
        request.setCurrent(1);
        request.setPageSize(Integer.MAX_VALUE);
        List<CustomerConversionReportService.DetailRow> rows = reportService.detail(request, orgId, userId).list();
        String eventLabel = EVENT_LABELS.getOrDefault(request.getEventType(), "客户转化");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(output)
                .head(List.of(List.of("客户"), List.of("手机号"), List.of("负责人/签约人"),
                        List.of("部门"), List.of("业务日期")))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet(eventLabel + "明细").build();
            writer.write(rows.stream().map(row -> List.of(
                    value(row.getCustomerName()), value(row.getCustomerMobile()), value(row.getEmployeeName()),
                    value(row.getDepartmentName()), formatDate(row.getEventTime()))).toList(), sheet);
        }
        return buildResponse(output.toByteArray(), "客户转化-" + eventLabel + "明细.xlsx");
    }

    private ResponseEntity<ByteArrayResource> buildResponse(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + UriUtils.encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private String formatDate(Long time) {
        if (time == null) {
            return "";
        }
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
