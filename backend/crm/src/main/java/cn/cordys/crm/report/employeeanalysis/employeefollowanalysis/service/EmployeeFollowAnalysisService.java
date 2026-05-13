package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.service.BaseService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisSummaryRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisSummaryItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisDimensionType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeFollowAnalysisMapper;
import cn.cordys.crm.system.dto.field.base.OptionProp;
import cn.cordys.crm.system.service.ModuleFieldExtService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class, readOnly = true)
public class EmployeeFollowAnalysisService {

    private static final String CUSTOMER_SOURCE_INTERNAL_KEY = "customerSource";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private EmployeeFollowAnalysisMapper employeeFollowAnalysisMapper;
    @Resource
    private ModuleFieldExtService moduleFieldExtService;
    @Resource
    private BaseService baseService;

    public List<EmployeeFollowAnalysisSummaryItemResponse> summary(EmployeeFollowAnalysisSummaryRequest request, String orgId) {
        // 汇总查询固定拆成两段：昨天及以前走事实表，今天走原始表实时聚合。
        QueryRange range = buildQueryRange(request.getTimePreset(), request.getStartTime(), request.getEndTime());
        List<EmployeeFollowAnalysisMetricRow> metricRows = new ArrayList<>();
        if (range.getHistoryStartDate() != null && range.getHistoryEndDate() != null) {
            // 历史区间 9 个指标统一从事实日报表读取。
            metricRows.addAll(employeeFollowAnalysisMapper.listFactRows(range.getHistoryStartDate().toString(), range.getHistoryEndDate().toString()));
        }
        if (range.getTodayStartTime() != null && range.getTodayEndTime() != null) {
            // 入库客户数
            metricRows.addAll(employeeFollowAnalysisMapper.listInboundRows(range.getTodayStartTime(), range.getTodayEndTime(), orgId));
            // 联系客户数
            metricRows.addAll(employeeFollowAnalysisMapper.listContactedRows(range.getTodayStartTime(), range.getTodayEndTime(), orgId));
            // 通话类指标：拨打电话数、拨打接通数、一分钟以上通话数、三分钟以上通话数、通话时长
            metricRows.addAll(employeeFollowAnalysisMapper.listCallRows(range.getTodayStartTime(), range.getTodayEndTime(), orgId));
            // 新增微信好友数
            metricRows.addAll(employeeFollowAnalysisMapper.listWechatRows(range.getTodayStartTime(), range.getTodayEndTime(), orgId));
        }

        EmployeeFollowAnalysisDimensionType dimensionType = EmployeeFollowAnalysisDimensionType.fromValue(request.getDimensionType());
        AggregationContext context = buildAggregationContext(metricRows, orgId);
        Map<String, SummaryAccumulator> accumulatorMap = aggregateRows(metricRows, dimensionType, context);
        if (Boolean.TRUE.equals(request.getShowEmptyItems())) {
            fillEmptyDimensions(accumulatorMap, dimensionType, context, range);
        }
        List<EmployeeFollowAnalysisSummaryItemResponse> responses = buildSummaryResponses(accumulatorMap, dimensionType, context);
        sortSummaryResponses(responses, dimensionType, context);
        return responses;
    }

    private AggregationContext buildAggregationContext(List<EmployeeFollowAnalysisMetricRow> metricRows, String orgId) {
        AggregationContext context = new AggregationContext();
        // 维度展示统一取当前值，不在历史事实表内固化部门和客户来源。
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = employeeFollowAnalysisMapper.listCurrentEmployees(orgId);
        Map<String, UserDeptDTO> userDeptMap = baseService.getUserDeptMapByUserIds(
                employees.stream().map(EmployeeFollowAnalysisEmployeeDimensionRow::getOperatorUserId).toList(), orgId);
        Map<String, EmployeeFollowAnalysisEmployeeDimensionRow> employeeMap = new LinkedHashMap<>();
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            UserDeptDTO userDeptDTO = userDeptMap.get(item.getOperatorUserId());
            if (userDeptDTO != null) {
                item.setDepartmentId(userDeptDTO.getDeptId());
                item.setDepartmentName(userDeptDTO.getDeptName());
            }
            employeeMap.put(item.getOperatorUserId(), item);
        }
        context.setEmployeeMap(employeeMap);
        Map<String, Integer> employeeOrderMap = new LinkedHashMap<>();
        for (int i = 0; i < employees.size(); i++) {
            employeeOrderMap.put(employees.get(i).getOperatorUserId(), i);
        }
        context.setEmployeeOrderMap(employeeOrderMap);

        // 部门维度只保留“当前有员工实际归属”的部门，不平铺整棵组织树所有节点。
        Map<String, String> departmentMap = new LinkedHashMap<>();
        Map<String, Integer> departmentOrderMap = new LinkedHashMap<>();
        int departmentIndex = 0;
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            if (StringUtils.isBlank(item.getDepartmentId())) {
                continue;
            }
            if (!departmentMap.containsKey(item.getDepartmentId())) {
                departmentMap.put(item.getDepartmentId(), item.getDepartmentName());
                departmentOrderMap.put(item.getDepartmentId(), departmentIndex++);
            }
        }
        context.setDepartmentMap(departmentMap);
        context.setDepartmentOrderMap(departmentOrderMap);

        Map<String, String> sourceLabelMap = new LinkedHashMap<>();
        List<OptionProp> sourceOptions = moduleFieldExtService.getFieldOptions(FormKey.CUSTOMER.getKey(), orgId, CUSTOMER_SOURCE_INTERNAL_KEY);
        Map<String, Integer> sourceOrderMap = new LinkedHashMap<>();
        int sourceIndex = 0;
        for (OptionProp option : sourceOptions) {
            sourceLabelMap.put(StringUtils.defaultString(option.getValue()), option.getLabel());
            sourceOrderMap.put(StringUtils.defaultString(option.getValue()), sourceIndex++);
        }
        context.setSourceLabelMap(sourceLabelMap);
        context.setSourceOrderMap(sourceOrderMap);

        Set<String> customerIds = new LinkedHashSet<>();
        for (EmployeeFollowAnalysisMetricRow row : metricRows) {
            if (StringUtils.isNotBlank(row.getCustomerId())) {
                customerIds.add(row.getCustomerId());
            }
        }
        Map<String, EmployeeFollowAnalysisCustomerContextRow> customerContextMap = new LinkedHashMap<>();
        if (!customerIds.isEmpty()) {
            List<EmployeeFollowAnalysisCustomerContextRow> customerRows = employeeFollowAnalysisMapper.listCustomerContexts(new ArrayList<>(customerIds), orgId);
            for (EmployeeFollowAnalysisCustomerContextRow item : customerRows) {
                customerContextMap.put(item.getCustomerId(), item);
            }
        }
        context.setCustomerContextMap(customerContextMap);
        return context;
    }

    private Map<String, SummaryAccumulator> aggregateRows(List<EmployeeFollowAnalysisMetricRow> metricRows,
                                                          EmployeeFollowAnalysisDimensionType dimensionType,
                                                          AggregationContext context) {
        Map<String, SummaryAccumulator> result = new LinkedHashMap<>();
        for (EmployeeFollowAnalysisMetricRow row : metricRows) {
            DimensionValue dimension = resolveDimensionValue(row, dimensionType, context);
            SummaryAccumulator accumulator = result.computeIfAbsent(dimension.getKey(), ignore -> new SummaryAccumulator(dimension.getKey(), dimension.getLabel()));
            // 这三个指标按客户去重，保证和“联系客户数/新增微信好友数”的业务口径一致。
            accumulator.getInboundCustomers().addIfFlag(row.getCustomerId(), row.getInboundCustomerFlag());
            accumulator.getContactedCustomers().addIfFlag(row.getCustomerId(), row.getContactedCustomerFlag());
            accumulator.getWechatCustomers().addIfFlag(row.getCustomerId(), row.getNewWechatFriendFlag());
            accumulator.setDialCount(accumulator.getDialCount() + defaultInt(row.getDialCount()));
            accumulator.setConnectedCount(accumulator.getConnectedCount() + defaultInt(row.getConnectedCount()));
            accumulator.setCallOver1MinCount(accumulator.getCallOver1MinCount() + defaultInt(row.getCallOver1minCount()));
            accumulator.setCallOver3MinCount(accumulator.getCallOver3MinCount() + defaultInt(row.getCallOver3minCount()));
            accumulator.setCallDurationSec(accumulator.getCallDurationSec() + defaultLong(row.getCallDurationSec()));
        }
        return result;
    }

    private void fillEmptyDimensions(Map<String, SummaryAccumulator> accumulatorMap,
                                     EmployeeFollowAnalysisDimensionType dimensionType,
                                     AggregationContext context,
                                     QueryRange range) {
        // “显示无数据项”只补当前维度全集，不额外制造不存在的事实数据。
        switch (dimensionType) {
            case EMPLOYEE_NAME -> {
                for (EmployeeFollowAnalysisEmployeeDimensionRow item : context.getEmployeeMap().values()) {
                    accumulatorMap.computeIfAbsent(item.getOperatorUserId(),
                            ignore -> new SummaryAccumulator(item.getOperatorUserId(), defaultText(item.getEmployeeName())));
                }
            }
            case EMPLOYEE_DEPT -> {
                for (Map.Entry<String, String> entry : context.getDepartmentMap().entrySet()) {
                    accumulatorMap.computeIfAbsent(entry.getKey(),
                            ignore -> new SummaryAccumulator(entry.getKey(), defaultText(entry.getValue())));
                }
            }
            case CUSTOMER_SOURCE -> {
                for (Map.Entry<String, String> entry : context.getSourceLabelMap().entrySet()) {
                    accumulatorMap.computeIfAbsent(entry.getKey(),
                            ignore -> new SummaryAccumulator(entry.getKey(), defaultText(entry.getValue())));
                }
            }
            case STAT_DAY -> {
                LocalDate cursor = range.getQueryStartDate();
                while (!cursor.isAfter(range.getQueryEndDate())) {
                    String key = cursor.toString();
                    accumulatorMap.computeIfAbsent(key, ignore -> new SummaryAccumulator(key, key));
                    cursor = cursor.plusDays(1);
                }
            }
            case STAT_MONTH -> {
                YearMonth cursor = YearMonth.from(range.getQueryStartDate());
                YearMonth end = YearMonth.from(range.getQueryEndDate());
                while (!cursor.isAfter(end)) {
                    String key = cursor.format(MONTH_FORMATTER);
                    accumulatorMap.computeIfAbsent(key, ignore -> new SummaryAccumulator(key, key));
                    cursor = cursor.plusMonths(1);
                }
            }
        }
    }

    private List<EmployeeFollowAnalysisSummaryItemResponse> buildSummaryResponses(Map<String, SummaryAccumulator> accumulatorMap,
                                                                                  EmployeeFollowAnalysisDimensionType dimensionType,
                                                                                  AggregationContext context) {
        List<EmployeeFollowAnalysisSummaryItemResponse> result = new ArrayList<>(accumulatorMap.size());
        for (SummaryAccumulator accumulator : accumulatorMap.values()) {
            EmployeeFollowAnalysisSummaryItemResponse item = new EmployeeFollowAnalysisSummaryItemResponse();
            item.setDimensionKey(accumulator.getDimensionKey());
            item.setDimensionLabel(resolveDisplayLabel(accumulator, dimensionType, context));
            // 入库客户数
            item.setInboundCustomerCount(accumulator.getInboundCustomers().size());
            // 联系客户数
            item.setContactedCustomerCount(accumulator.getContactedCustomers().size());
            // 新增微信好友数
            item.setNewWechatFriendCount(accumulator.getWechatCustomers().size());
            // 拨打电话数
            item.setDialCount(accumulator.getDialCount());
            // 拨打接通数
            item.setConnectedCount(accumulator.getConnectedCount());
            // 一分钟以上通话数
            item.setCallOver1MinCount(accumulator.getCallOver1MinCount());
            // 三分钟以上通话数
            item.setCallOver3MinCount(accumulator.getCallOver3MinCount());
            // 通话时长
            item.setCallDurationSec(accumulator.getCallDurationSec());
            // 平均通话时长 = 通话时长 / 拨打接通数
            item.setAvgCallDurationSec(accumulator.getConnectedCount() > 0 ? accumulator.getCallDurationSec() / accumulator.getConnectedCount() : 0L);
            result.add(item);
        }
        return result;
    }

    private void sortSummaryResponses(List<EmployeeFollowAnalysisSummaryItemResponse> responses,
                                      EmployeeFollowAnalysisDimensionType dimensionType,
                                      AggregationContext context) {
        Comparator<EmployeeFollowAnalysisSummaryItemResponse> comparator = switch (dimensionType) {
            case EMPLOYEE_NAME -> Comparator.<EmployeeFollowAnalysisSummaryItemResponse>comparingInt(item -> orderOf(context.getEmployeeOrderMap(), item.getDimensionKey()))
                    .thenComparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionLabel, String::compareTo);
            case EMPLOYEE_DEPT -> Comparator.<EmployeeFollowAnalysisSummaryItemResponse>comparingInt(item -> orderOf(context.getDepartmentOrderMap(), item.getDimensionKey()))
                    .thenComparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionLabel, String::compareTo);
            case CUSTOMER_SOURCE -> Comparator.<EmployeeFollowAnalysisSummaryItemResponse>comparingInt(item -> orderOf(context.getSourceOrderMap(), item.getDimensionKey()))
                    .thenComparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionLabel, String::compareTo);
            case STAT_DAY, STAT_MONTH -> Comparator.comparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionKey, String::compareTo);
        };
        responses.sort(comparator);
    }

    private String resolveDisplayLabel(SummaryAccumulator accumulator,
                                       EmployeeFollowAnalysisDimensionType dimensionType,
                                       AggregationContext context) {
        return switch (dimensionType) {
            case EMPLOYEE_NAME, EMPLOYEE_DEPT, STAT_DAY, STAT_MONTH -> accumulator.getDimensionLabel();
            case CUSTOMER_SOURCE -> {
                if (StringUtils.isBlank(accumulator.getDimensionKey())) {
                    yield defaultText(accumulator.getDimensionLabel());
                }
                yield defaultText(context.getSourceLabelMap().getOrDefault(accumulator.getDimensionKey(), accumulator.getDimensionKey()));
            }
        };
    }

    private DimensionValue resolveDimensionValue(EmployeeFollowAnalysisMetricRow row,
                                                 EmployeeFollowAnalysisDimensionType dimensionType,
                                                 AggregationContext context) {
        return switch (dimensionType) {
            case EMPLOYEE_NAME -> {
                EmployeeFollowAnalysisEmployeeDimensionRow employee = context.getEmployeeMap().get(row.getOperatorUserId());
                String key = StringUtils.defaultString(row.getOperatorUserId());
                yield new DimensionValue(key, defaultText(employee == null ? null : employee.getEmployeeName()));
            }
            case EMPLOYEE_DEPT -> {
                EmployeeFollowAnalysisEmployeeDimensionRow employee = context.getEmployeeMap().get(row.getOperatorUserId());
                String departmentId = employee == null ? "" : StringUtils.defaultString(employee.getDepartmentId());
                String departmentName = employee == null ? "" : employee.getDepartmentName();
                yield new DimensionValue(departmentId, defaultText(departmentName));
            }
            case CUSTOMER_SOURCE -> {
                EmployeeFollowAnalysisCustomerContextRow customer = context.getCustomerContextMap().get(row.getCustomerId());
                String sourceValue = customer == null ? "" : StringUtils.defaultString(customer.getCustomerSource());
                yield new DimensionValue(sourceValue, defaultText(context.getSourceLabelMap().getOrDefault(sourceValue, sourceValue)));
            }
            case STAT_DAY -> new DimensionValue(row.getStatDate(), row.getStatDate());
            case STAT_MONTH -> new DimensionValue(row.getStatDate().substring(0, 7), row.getStatDate().substring(0, 7));
        };
    }

    private QueryRange buildQueryRange(String timePresetValue, Long customStartTime, Long customEndTime) {
        EmployeeFollowAnalysisTimePreset timePreset = EmployeeFollowAnalysisTimePreset.fromValue(timePresetValue);
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        switch (timePreset) {
            case TODAY -> {
                startDate = today;
                endDate = today;
            }
            case YESTERDAY -> {
                startDate = today.minusDays(1);
                endDate = today.minusDays(1);
            }
            case WEEK -> {
                startDate = today.with(DayOfWeek.MONDAY);
                endDate = today;
            }
            case MONTH -> {
                startDate = today.withDayOfMonth(1);
                endDate = today;
            }
            case CUSTOM -> {
                startDate = toLocalDate(customStartTime);
                endDate = toLocalDate(customEndTime);
            }
            default -> throw new IllegalStateException("unexpected value: " + timePreset);
        }
        QueryRange range = new QueryRange();
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
        range.setQueryStartDate(startDate);
        range.setQueryEndDate(endDate);
        LocalDate yesterday = today.minusDays(1);
        // 自定义区间只要覆盖今天，就会自动拆成“历史 + today”两段。
        if (!startDate.isAfter(yesterday)) {
            range.setHistoryStartDate(startDate);
            range.setHistoryEndDate(endDate.isAfter(yesterday) ? yesterday : endDate);
        }
        if (!endDate.isBefore(today)) {
            range.setTodayStartTime(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            range.setTodayEndTime(System.currentTimeMillis());
        }
        return range;
    }

    private LocalDate toLocalDate(Long timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("custom range timestamp is required");
        }
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String defaultText(String value) {
        return StringUtils.defaultIfBlank(value, "-");
    }

    private int orderOf(Map<String, Integer> orderMap, String key) {
        return orderMap.getOrDefault(StringUtils.defaultString(key), Integer.MAX_VALUE);
    }

    @Data
    private static class QueryRange {
        private LocalDate queryStartDate;
        private LocalDate queryEndDate;
        private LocalDate historyStartDate;
        private LocalDate historyEndDate;
        private Long todayStartTime;
        private Long todayEndTime;
    }

    @Data
    private static class AggregationContext {
        private Map<String, EmployeeFollowAnalysisEmployeeDimensionRow> employeeMap = new LinkedHashMap<>();
        private Map<String, Integer> employeeOrderMap = new LinkedHashMap<>();
        private Map<String, String> departmentMap = new LinkedHashMap<>();
        private Map<String, Integer> departmentOrderMap = new LinkedHashMap<>();
        private Map<String, String> sourceLabelMap = new LinkedHashMap<>();
        private Map<String, Integer> sourceOrderMap = new LinkedHashMap<>();
        private Map<String, EmployeeFollowAnalysisCustomerContextRow> customerContextMap = new LinkedHashMap<>();
    }

    @Data
    private static class DimensionValue {
        private final String key;
        private final String label;
    }

    @Data
    private static class SummaryAccumulator {
        private final String dimensionKey;
        private final String dimensionLabel;
        private final UniqueCustomerSet inboundCustomers = new UniqueCustomerSet();
        private final UniqueCustomerSet contactedCustomers = new UniqueCustomerSet();
        private final UniqueCustomerSet wechatCustomers = new UniqueCustomerSet();
        private int dialCount;
        private int connectedCount;
        private int callOver1MinCount;
        private int callOver3MinCount;
        private long callDurationSec;
    }

    private static class UniqueCustomerSet {
        private final Set<String> customerIds = new LinkedHashSet<>();

        void addIfFlag(String customerId, Integer flag) {
            if (flag != null && flag > 0 && StringUtils.isNotBlank(customerId)) {
                customerIds.add(customerId);
            }
        }

        int size() {
            return customerIds.size();
        }
    }
}
