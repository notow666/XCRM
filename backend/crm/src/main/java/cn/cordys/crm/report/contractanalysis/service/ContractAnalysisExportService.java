package cn.cordys.crm.report.contractanalysis.service;

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
public class ContractAnalysisExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<String, String> METRIC_LABELS = Map.of(
            "CONTRACT", "合同",
            "LOAN", "放款金额",
            "REPAYMENT", "回款金额",
            "REVENUE", "创收金额");

    @Resource
    private ContractAnalysisService analysisService;

    public ResponseEntity<ByteArrayResource> export(ContractAnalysisService.QueryRequest request,
                                                    String orgId, String userId) {
        List<ContractAnalysisService.SummaryRow> rows = analysisService.summary(request, orgId, userId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(output)
                .head(List.of(List.of("统计维度"), List.of("合同签约数"), List.of("合同签约金额"),
                        List.of("放款金额"), List.of("回款金额"), List.of("创收金额")))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet("合同成交分析").build();
            writer.write(rows.stream().map(row -> List.of(row.getDimensionLabel(), row.getContractCount(),
                    row.getContractAmount(), row.getLoanAmount(), row.getRepaymentAmount(),
                    row.getRevenueAmount())).toList(), sheet);
        }
        byte[] bytes = output.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + UriUtils.encode("合同成交分析.xlsx", StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    public ResponseEntity<ByteArrayResource> exportDetail(ContractAnalysisService.DetailRequest request,
                                                          String orgId, String userId) {
        request.setCurrent(1);
        request.setPageSize(Integer.MAX_VALUE);
        List<ContractAnalysisService.DetailRow> rows = analysisService.detail(request, orgId, userId).list();
        String metricLabel = METRIC_LABELS.getOrDefault(request.getMetricType(), "合同");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(output)
                .head(List.of(List.of("合同"), List.of("客户"), List.of("手机号"), List.of("签约人"),
                        List.of("部门"), List.of("客户来源"), List.of("业务日期"), List.of("金额")))
                .excelType(ExcelTypeEnum.XLSX)
                .registerWriteHandler(new CustomHeadColWidthStyleStrategy())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet(metricLabel + "明细").build();
            writer.write(rows.stream().map(row -> List.of(
                    value(row.getContractName()), value(row.getCustomerName()), value(row.getCustomerMobile()),
                    value(row.getSignerName()), value(row.getDepartmentName()), value(row.getCustomerSource()),
                    formatDate(row.getBusinessTime()), row.getAmount())).toList(), sheet);
        }
        return buildResponse(output.toByteArray(), "合同成交分析-" + metricLabel + "明细.xlsx");
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
