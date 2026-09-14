package cn.cordys.crm.report.customerconversion.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.BaseTreeNode;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.PhoneMaskUtil;
import cn.cordys.crm.report.customerconversion.constants.CustomerConversionEventType;
import cn.cordys.crm.report.mapper.ReportAggregateMapper;
import cn.cordys.crm.system.service.DepartmentService;
import cn.cordys.crm.system.service.GlobalPhoneMaskConfigService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 客户转化报表：签约/回款客户数、回款客户数（按签约人归属）。
 * 数据源为历史事件表（report_employee_stat_event 上门 + report_customer_conversion_event 签约/回款），
 * 业务删除后事件保留仍可统计；聚合下沉到 SQL GROUP BY，避免大数据量下全量加载。
 */
@Service
public class CustomerConversionReportService {

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

        // 涉及员工去重，按实时部门做可见性过滤后下推到 SQL（三组 distinct 并行）
        CompletableFuture<Set<String>> visitEmployeesFuture = CompletableFuture.supplyAsync(() -> visibleEmployees(
                new HashSet<>(reportAggregateMapper.distinctVisitEmployees(orgId, range.startTime(), range.endTime())),
                orgId, userId, visibilityScope), parallelExecutor);
        CompletableFuture<Set<String>> signedEmployeesFuture = CompletableFuture.supplyAsync(() -> visibleEmployees(
                new HashSet<>(reportAggregateMapper.distinctConversionEmployees(orgId, CustomerConversionEventType.CONTRACT_SIGNED.name(), range.startTime(), range.endTime())),
                orgId, userId, visibilityScope), parallelExecutor);
        CompletableFuture<Set<String>> paidEmployeesFuture = CompletableFuture.supplyAsync(() -> visibleEmployees(
                new HashSet<>(reportAggregateMapper.distinctConversionEmployees(orgId, CustomerConversionEventType.PAYMENT_APPROVED.name(), range.startTime(), range.endTime())),
                orgId, userId, visibilityScope), parallelExecutor);
        Set<String> visitEmployees = visitEmployeesFuture.join();
        Set<String> signedEmployees = signedEmployeesFuture.join();
        Set<String> paidEmployees = paidEmployeesFuture.join();

        // SQL 聚合：上门 / 签约 / 回款客户数（COUNT(DISTINCT customer_id)，按维度分组，并行执行）
        CompletableFuture<List<ReportAggregateMapper.ConversionAggregateRow>> visitFuture = visitEmployees.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> reportAggregateMapper.countVisitCustomers(
                        orgId, range.startTime(), range.endTime(), dimensionType, visitEmployees), parallelExecutor);
        CompletableFuture<List<ReportAggregateMapper.ConversionAggregateRow>> signedFuture = signedEmployees.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> reportAggregateMapper.countConversionCustomers(
                        orgId, CustomerConversionEventType.CONTRACT_SIGNED.name(), range.startTime(), range.endTime(),
                        dimensionType, signedEmployees), parallelExecutor);
        CompletableFuture<List<ReportAggregateMapper.ConversionAggregateRow>> paidFuture = paidEmployees.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> reportAggregateMapper.countConversionCustomers(
                        orgId, CustomerConversionEventType.PAYMENT_APPROVED.name(), range.startTime(), range.endTime(),
                        dimensionType, paidEmployees), parallelExecutor);

        Map<String, SummaryAccumulator> buckets = new LinkedHashMap<>();
        for (ReportAggregateMapper.ConversionAggregateRow row : visitFuture.join()) {
            SummaryAccumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new SummaryAccumulator(row.getDimKey(), row.getDimLabel()));
            acc.visitCustomers += row.getCustomerCount();
        }
        for (ReportAggregateMapper.ConversionAggregateRow row : signedFuture.join()) {
            SummaryAccumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new SummaryAccumulator(row.getDimKey(), row.getDimLabel()));
            acc.signedCustomers += row.getCustomerCount();
        }
        for (ReportAggregateMapper.ConversionAggregateRow row : paidFuture.join()) {
            SummaryAccumulator acc = buckets.computeIfAbsent(row.getDimKey(), k -> new SummaryAccumulator(row.getDimKey(), row.getDimLabel()));
            acc.paymentCustomers += row.getCustomerCount();
        }
        return buckets.values().stream().map(SummaryAccumulator::toResponse).collect(Collectors.toList());
    }

    public PageResult<DetailRow> detail(DetailRequest request, String orgId, String userId) {
        Range range = normalizeRange(request);
        String dimensionType = StringUtils.defaultIfBlank(request.getDimensionType(), "EMPLOYEE_NAME");
        VisibilityScope visibilityScope = resolveVisibilityScope(orgId, userId, request.getDepartmentId());
        String eventType = StringUtils.defaultIfBlank(request.getEventType(), CustomerConversionEventType.CONTRACT_SIGNED.name());
        int offset = (request.getCurrent() - 1) * request.getPageSize();
        boolean phoneMaskEnabled = globalPhoneMaskConfigService.isEnabled(orgId);

        List<DetailRow> rows;
        long total;
        if (StringUtils.equals(eventType, "VISIT")) {
            Set<String> visitEmployees = visibleEmployees(
                    new HashSet<>(reportAggregateMapper.distinctVisitEmployees(orgId, range.startTime(), range.endTime())),
                    orgId, userId, visibilityScope);
            if (visitEmployees.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            rows = toDetailRows(reportAggregateMapper.listVisitDetails(orgId, range.startTime(), range.endTime(),
                    visitEmployees, dimensionType, request.getDimensionKey(), offset, request.getPageSize()),
                    phoneMaskEnabled);
            total = reportAggregateMapper.countVisitDetails(orgId, range.startTime(), range.endTime(),
                    visitEmployees, dimensionType, request.getDimensionKey());
        } else {
            Set<String> employees = visibleEmployees(
                    new HashSet<>(reportAggregateMapper.distinctConversionEmployees(orgId, eventType, range.startTime(), range.endTime())),
                    orgId, userId, visibilityScope);
            if (employees.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            rows = toDetailRows(reportAggregateMapper.listConversionDetails(orgId, eventType, range.startTime(), range.endTime(),
                    employees, dimensionType, request.getDimensionKey(), offset, request.getPageSize()),
                    phoneMaskEnabled);
            total = reportAggregateMapper.countConversionDetails(orgId, eventType, range.startTime(), range.endTime(),
                    employees, dimensionType, request.getDimensionKey());
        }
        return new PageResult<>(total, rows);
    }

    private Set<String> visibleEmployees(Set<String> employees, String orgId, String userId, VisibilityScope scope) {
        if (employees.isEmpty()) {
            return employees;
        }
        Map<String, UserDeptDTO> deptMap = baseService.getUserDeptMapByUserIds(new ArrayList<>(employees), orgId);
        return employees.stream()
                .filter(employeeId -> {
                    UserDeptDTO dept = deptMap.get(employeeId);
                    String deptId = dept == null ? null : dept.getDeptId();
                    return isVisible(employeeId, deptId, userId, scope);
                })
                .collect(Collectors.toSet());
    }

    private List<DetailRow> toDetailRows(List<ReportAggregateMapper.ConversionDetailRow> rows,
                                         boolean phoneMaskEnabled) {
        List<DetailRow> result = new ArrayList<>(rows.size());
        for (ReportAggregateMapper.ConversionDetailRow row : rows) {
            DetailRow d = new DetailRow();
            d.setEventType(row.getEventType());
            d.setBusinessId(row.getBusinessId());
            d.setCustomerId(row.getCustomerId());
            d.setCustomerName(row.getCustomerName());
            d.setCustomerMobile(phoneMaskEnabled
                    ? PhoneMaskUtil.maskGlobalPhone(row.getCustomerMobile())
                    : row.getCustomerMobile());
            d.setEmployeeId(row.getEmployeeId());
            d.setEmployeeName(row.getEmployeeName());
            d.setDepartmentId(row.getDepartmentId());
            d.setDepartmentName(row.getDepartmentName());
            d.setEventTime(row.getEventTime());
            d.setStatDate(row.getStatDate());
            result.add(d);
        }
        return result;
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

    private boolean isVisible(String employeeId, String deptId, String userId, VisibilityScope scope) {
        if (scope.all()) {
            return true;
        }
        if (scope.departmentFiltered()) {
            return deptId != null && scope.deptIds().contains(deptId);
        }
        return StringUtils.equals(employeeId, userId) || (deptId != null && scope.deptIds().contains(deptId));
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

    private record Range(long startTime, long endTime) {
    }

    private record VisibilityScope(boolean all, boolean departmentFiltered, Set<String> deptIds) {
    }

    private static final class SummaryAccumulator {
        private final String dimKey;
        private final String dimLabel;
        private long visitCustomers;
        private long signedCustomers;
        private long paymentCustomers;

        private SummaryAccumulator(String dimKey, String dimLabel) {
            this.dimKey = dimKey;
            this.dimLabel = dimLabel;
        }

        private SummaryRow toResponse() {
            SummaryRow row = new SummaryRow();
            row.setDimensionKey(StringUtils.defaultString(dimKey, "-"));
            row.setDimensionLabel(StringUtils.defaultIfBlank(dimLabel, "未分配"));
            row.setVisitCustomerCount(visitCustomers);
            row.setSignedCustomerCount(signedCustomers);
            row.setPaymentCustomerCount(paymentCustomers);
            row.setSignRate(rate(signedCustomers, visitCustomers));
            row.setPaymentRate(rate(paymentCustomers, visitCustomers));
            return row;
        }

        private String rate(long numerator, long denominator) {
            if (denominator == 0) {
                return "0.00%";
            }
            return BigDecimal.valueOf(numerator * 100L)
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP) + "%";
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
        private String eventType;
        private String dimensionKey;
        private int current = 1;
        private int pageSize = 20;
    }

    @Data
    public static class SummaryRow {
        private String dimensionKey;
        private String dimensionLabel;
        private long visitCustomerCount;
        private long signedCustomerCount;
        private long paymentCustomerCount;
        private String signRate;
        private String paymentRate;
    }

    @Data
    public static class DetailRow {
        private String eventType;
        private String businessId;
        private String customerId;
        private String customerName;
        private String customerMobile;
        private String employeeId;
        private String employeeName;
        private String departmentId;
        private String departmentName;
        private Long eventTime;
        private LocalDate statDate;
    }

    public record PageResult<T>(long total, List<T> list) {
    }
}
