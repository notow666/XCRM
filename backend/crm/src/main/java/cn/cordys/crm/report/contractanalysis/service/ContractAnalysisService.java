package cn.cordys.crm.report.contractanalysis.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.BaseTreeNode;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.PhoneMaskUtil;
import cn.cordys.crm.report.mapper.ReportAggregateMapper;
import cn.cordys.crm.system.service.DepartmentService;
import cn.cordys.crm.system.service.GlobalPhoneMaskConfigService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 合同成交分析报表：签约数/金额、放款、回款、创收（按签约人归属）。
 * 实时口径：直接查询合同/回款产品主表（合同删除后不再统计），聚合下沉到 SQL GROUP BY。
 */
@Service
public class ContractAnalysisService {

    @Resource
    private ReportAggregateMapper reportAggregateMapper;
    @Resource
    private BaseService baseService;
    @Resource
    private DepartmentService departmentService;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private GlobalPhoneMaskConfigService globalPhoneMaskConfigService;
    @Resource(name = ExecutorBeanNames.PARALLEL)
    private Executor parallelExecutor;

    public List<SummaryRow> summary(QueryRequest request, String orgId, String userId) {
        Range range = normalizeRange(request);
        String dimensionType = StringUtils.defaultIfBlank(request.getDimensionType(), "EMPLOYEE_NAME");
        VisibilityScope visibilityScope = resolveVisibilityScope(orgId, userId, request.getDepartmentId());

        Set<String> signers = visibleSigners(
                new HashSet<>(reportAggregateMapper.distinctContractSigners(orgId, range.startTime(), range.endTime())),
                orgId, userId, visibilityScope);
        if (signers.isEmpty()) {
            return List.of();
        }

        // 三个聚合查询相互独立，并行执行（MySQL 聚合为 CPU 密集，串行会累加耗时）
        CompletableFuture<List<ReportAggregateMapper.ContractAggregateRow>> contractFuture = CompletableFuture.supplyAsync(
                () -> reportAggregateMapper.aggregateContracts(orgId, range.startTime(), range.endTime(), dimensionType, signers),
                parallelExecutor);
        CompletableFuture<List<ReportAggregateMapper.ContractAggregateRow>> loanFuture = CompletableFuture.supplyAsync(
                () -> reportAggregateMapper.aggregateProducts(orgId, range.startTime(), range.endTime(), dimensionType, signers),
                parallelExecutor);
        CompletableFuture<List<ReportAggregateMapper.ContractAggregateRow>> repayFuture = CompletableFuture.supplyAsync(
                () -> reportAggregateMapper.aggregateProductsRepayment(orgId, range.startTime(), range.endTime(), dimensionType, signers),
                parallelExecutor);

        Map<String, Accumulator> buckets = new LinkedHashMap<>();
        for (ReportAggregateMapper.ContractAggregateRow row : contractFuture.join()) {
            Accumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new Accumulator(row.getDimKey(), row.getDimLabel()));
            acc.contractCount = (int) row.getContractCount();
            acc.contractAmount = zero(row.getContractAmount());
        }
        for (ReportAggregateMapper.ContractAggregateRow row : loanFuture.join()) {
            Accumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new Accumulator(row.getDimKey(), row.getDimLabel()));
            acc.loanAmount = acc.loanAmount.add(zero(row.getLoanAmount()));
        }
        for (ReportAggregateMapper.ContractAggregateRow row : repayFuture.join()) {
            Accumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new Accumulator(row.getDimKey(), row.getDimLabel()));
            acc.repaymentAmount = acc.repaymentAmount.add(zero(row.getRepaymentAmount()));
            acc.revenueAmount = acc.revenueAmount.add(zero(row.getRevenueAmount()));
        }
        return buckets.values().stream().map(Accumulator::toResponse).collect(Collectors.toList());
    }

    public PageResult<DetailRow> detail(DetailRequest request, String orgId, String userId) {
        Range range = normalizeRange(request);
        String dimensionType = StringUtils.defaultIfBlank(request.getDimensionType(), "EMPLOYEE_NAME");
        VisibilityScope visibilityScope = resolveVisibilityScope(orgId, userId, request.getDepartmentId());
        String metricType = StringUtils.defaultIfBlank(request.getMetricType(), "CONTRACT");
        int offset = (request.getCurrent() - 1) * request.getPageSize();

        Set<String> signers = visibleSigners(
                new HashSet<>(reportAggregateMapper.distinctContractSigners(orgId, range.startTime(), range.endTime())),
                orgId, userId, visibilityScope);
        if (signers.isEmpty()) {
            return new PageResult<>(0, List.of());
        }

        List<ContractDetailRowAdapter> adapters = new ArrayList<>();
        long total;
        switch (metricType) {
            case "LOAN" -> {
                for (ReportAggregateMapper.ContractDetailRow row : reportAggregateMapper.listProductDetails(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey(), offset, request.getPageSize())) {
                    adapters.add(new ContractDetailRowAdapter(row, row.getAmount()));
                }
                total = reportAggregateMapper.countProductDetails(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey());
            }
            case "REPAYMENT", "REVENUE" -> {
                for (ReportAggregateMapper.ContractDetailRow row : reportAggregateMapper.listProductDetailsByRepayment(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey(), offset, request.getPageSize())) {
                    adapters.add(new ContractDetailRowAdapter(row, StringUtils.equals(metricType, "REVENUE") ? row.getRevenueAmount() : row.getAmount()));
                }
                total = reportAggregateMapper.countProductDetailsByRepayment(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey());
            }
            default -> {
                for (ReportAggregateMapper.ContractDetailRow row : reportAggregateMapper.listContractDetails(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey(), offset, request.getPageSize())) {
                    adapters.add(new ContractDetailRowAdapter(row, row.getAmount()));
                }
                total = reportAggregateMapper.countContractDetails(orgId, range.startTime(), range.endTime(),
                        signers, dimensionType, request.getDimensionKey());
            }
        }

        boolean phoneMaskEnabled = globalPhoneMaskConfigService.isEnabled(orgId);
        List<DetailRow> rows = new ArrayList<>(adapters.size());
        for (ContractDetailRowAdapter adapter : adapters) {
            ReportAggregateMapper.ContractDetailRow row = adapter.row();
            DetailRow d = new DetailRow();
            d.setMetricType(row.getMetricType());
            d.setResourceId(row.getResourceId());
            d.setPaymentRecordId(row.getPaymentRecordId());
            d.setContractId(row.getContractId());
            d.setContractName(row.getContractName());
            d.setCustomerId(row.getCustomerId());
            d.setCustomerName(row.getCustomerName());
            d.setCustomerMobile(phoneMaskEnabled
                    ? PhoneMaskUtil.maskGlobalPhone(row.getCustomerMobile())
                    : row.getCustomerMobile());
            d.setCustomerSource(row.getCustomerSource());
            d.setSignerId(row.getSignerId());
            d.setSignerName(row.getSignerName());
            d.setDepartmentId(row.getDepartmentId());
            d.setDepartmentName(row.getDepartmentName());
            d.setBusinessTime(row.getBusinessTime());
            d.setAmount(zero(adapter.amount()));
            rows.add(d);
        }
        return new PageResult<>(total, rows);
    }

    private Set<String> visibleSigners(Set<String> signers, String orgId, String userId, VisibilityScope scope) {
        if (signers.isEmpty()) {
            return signers;
        }
        Map<String, UserDeptDTO> deptMap = baseService.getUserDeptMapByUserIds(new ArrayList<>(signers), orgId);
        return signers.stream()
                .filter(signerId -> {
                    UserDeptDTO dept = deptMap.get(signerId);
                    String deptId = dept == null ? null : dept.getDeptId();
                    return isVisible(signerId, deptId, userId, scope);
                })
                .collect(Collectors.toSet());
    }

    private VisibilityScope resolveVisibilityScope(String orgId, String userId, String selectedDeptId) {
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(
                userId, orgId, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        Set<String> permittedDeptIds = new LinkedHashSet<>(permission.getDeptIds());
        if (StringUtils.isBlank(selectedDeptId)) {
            return new VisibilityScope(Boolean.TRUE.equals(permission.getAll()), false, permittedDeptIds);
        }
        List<BaseTreeNode> tree = departmentService.getTree(orgId);
        Set<String> selectedDeptIds = new LinkedHashSet<>(
                dataScopeService.getDeptIdsWithChild(tree, Set.of(selectedDeptId)));
        if (!Boolean.TRUE.equals(permission.getAll())) {
            selectedDeptIds.retainAll(permittedDeptIds);
        }
        return new VisibilityScope(false, true, selectedDeptIds);
    }

    private boolean isVisible(String signerId, String deptId, String userId, VisibilityScope scope) {
        if (scope.all()) {
            return true;
        }
        if (scope.departmentFiltered()) {
            return deptId != null && scope.deptIds().contains(deptId);
        }
        return StringUtils.equals(signerId, userId) || (deptId != null && scope.deptIds().contains(deptId));
    }

    private Range normalizeRange(QueryRequest request) {
        LocalDate today = LocalDate.now();
        long start = request.getStartTime() == null
                ? today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : request.getStartTime();
        long end = request.getEndTime() == null
                ? today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                : request.getEndTime();
        if (start > end) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        return new Range(start, end);
    }

    private LocalDate toDate(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Range(long startTime, long endTime) {
    }

    private record VisibilityScope(boolean all, boolean departmentFiltered, Set<String> deptIds) {
    }

    private record ContractDetailRowAdapter(ReportAggregateMapper.ContractDetailRow row, BigDecimal amount) {
    }

    private static final class Accumulator {
        private final String dimKey;
        private final String dimLabel;
        private int contractCount;
        private BigDecimal contractAmount = BigDecimal.ZERO;
        private BigDecimal loanAmount = BigDecimal.ZERO;
        private BigDecimal repaymentAmount = BigDecimal.ZERO;
        private BigDecimal revenueAmount = BigDecimal.ZERO;

        private Accumulator(String dimKey, String dimLabel) {
            this.dimKey = dimKey;
            this.dimLabel = dimLabel;
        }

        private SummaryRow toResponse() {
            SummaryRow row = new SummaryRow();
            row.setDimensionKey(StringUtils.defaultString(dimKey, "-"));
            row.setDimensionLabel(StringUtils.defaultIfBlank(dimLabel, "未分配"));
            row.setContractCount(contractCount);
            row.setContractAmount(contractAmount);
            row.setLoanAmount(loanAmount);
            row.setRepaymentAmount(repaymentAmount);
            row.setRevenueAmount(revenueAmount);
            return row;
        }
    }

    @Data
    public static class QueryRequest {
        private Long startTime;
        private Long endTime;
        private String dimensionType;
        private String departmentId;
    }

    @Data
    public static class DetailRequest extends QueryRequest {
        private String metricType;
        private String dimensionKey;
        private int current = 1;
        private int pageSize = 20;
    }

    @Data
    public static class SummaryRow {
        private String dimensionKey;
        private String dimensionLabel;
        private int contractCount;
        private BigDecimal contractAmount;
        private BigDecimal loanAmount;
        private BigDecimal repaymentAmount;
        private BigDecimal revenueAmount;
    }

    @Data
    public static class DetailRow {
        private String metricType;
        private String resourceId;
        private String paymentRecordId;
        private String contractId;
        private String contractName;
        private String customerId;
        private String customerName;
        private String customerMobile;
        private String customerSource;
        private String signerId;
        private String signerName;
        private String departmentId;
        private String departmentName;
        private Long businessTime;
        private BigDecimal amount;
    }

    public record PageResult<T>(long total, List<T> list) {
    }
}
