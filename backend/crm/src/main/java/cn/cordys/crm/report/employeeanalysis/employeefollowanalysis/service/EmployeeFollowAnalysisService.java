package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.BaseTreeNode;
import cn.cordys.common.util.JSON;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeStatDay;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisSummaryRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisSummaryItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisDimensionType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeStatAnalysisMapper;
import cn.cordys.crm.system.service.DepartmentService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Transactional(rollbackFor = Exception.class, readOnly = true)
@Slf4j
public class EmployeeFollowAnalysisService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private EmployeeStatAnalysisMapper employeeStatAnalysisMapper;
    @Resource
    private BaseService baseService;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private DepartmentService departmentService;

    public List<EmployeeFollowAnalysisSummaryItemResponse> summary(EmployeeFollowAnalysisSummaryRequest request, String orgId, String userId) {
        QueryRange range = buildQueryRange(request.getTimePreset(), request.getStartTime(), request.getEndTime());
        EmployeeFollowAnalysisDimensionType dimensionType = EmployeeFollowAnalysisDimensionType.fromValue(request.getDimensionType());
        AggregationContext context = buildAggregationContext(orgId, userId, request.getDepartmentId());
        if (context.getEmployeeMap().isEmpty()) {
            return List.of();
        }
        List<String> visibleOwnerUserIds = context.getSqlFilterOperatorUserIds();
        Map<String, SummaryAccumulator> accumulatorMap = new LinkedHashMap<>();
        boolean useHistoryDayRows = dimensionType != EmployeeFollowAnalysisDimensionType.CUSTOMER_SOURCE;

        if (useHistoryDayRows
                && range.getHistoryStartDate() != null
                && range.getHistoryEndDate() != null) {
            List<EmployeeStatDay> historyRows = logSqlQuery(
                    "listHistoryDayRows",
                    "startDate=" + range.getHistoryStartDate() + ", endDate=" + range.getHistoryEndDate() + ", orgId=" + orgId,
                    () -> employeeStatAnalysisMapper.listHistoryDayRows(
                            range.getHistoryStartDate().toString(),
                            range.getHistoryEndDate().toString(),
                            orgId,
                            visibleOwnerUserIds
                    )
            );
            mergeAccumulatorMaps(accumulatorMap, aggregateHistoryDayRows(historyRows, dimensionType));
        }

        Long metricStartTime;
        Long metricEndTime;
        if (useHistoryDayRows) {
            metricStartTime = range.getTodayStartTime();
            metricEndTime = range.getTodayEndTime();
        } else {
            metricStartTime = range.getQueryStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            metricEndTime = range.getQueryEndDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
        }

        if (metricStartTime != null && metricEndTime != null) {
            List<EmployeeFollowAnalysisMetricRow> metricRows = loadMetricRows(metricStartTime, metricEndTime, orgId, visibleOwnerUserIds);
            mergeAccumulatorMaps(accumulatorMap, aggregateRows(metricRows, dimensionType, context));
        }

        if (Boolean.TRUE.equals(request.getShowEmptyItems())) {
            fillEmptyDimensions(accumulatorMap, dimensionType, context, range);
        }
        List<EmployeeFollowAnalysisSummaryItemResponse> responses = buildSummaryResponses(accumulatorMap, dimensionType, context);
        sortSummaryResponses(responses, dimensionType, context);
        return responses;
    }

    private List<EmployeeFollowAnalysisMetricRow> loadMetricRows(Long startTime,
                                                                 Long endTime,
                                                                 String orgId,
                                                                 List<String> visibleOwnerUserIds) {
        List<EmployeeFollowAnalysisMetricRow> metricRows = new ArrayList<>();
        metricRows.addAll(logSqlQuery(
                "listInboundRows",
                "startTime=" + startTime + ", endTime=" + endTime + ", orgId=" + orgId,
                () -> employeeStatAnalysisMapper.listInboundRows(startTime, endTime, orgId, visibleOwnerUserIds)
        ));
        metricRows.addAll(logSqlQuery(
                "listContactedRows",
                "startTime=" + startTime + ", endTime=" + endTime + ", orgId=" + orgId,
                () -> employeeStatAnalysisMapper.listContactedRows(startTime, endTime, orgId, visibleOwnerUserIds)
        ));
        List<EmployeeFollowAnalysisMetricRow> callRows = logSqlQuery(
                "listCallRows",
                "startTime=" + startTime + ", endTime=" + endTime + ", orgId=" + orgId,
                () -> employeeStatAnalysisMapper.listCallRows(startTime, endTime, orgId, visibleOwnerUserIds)
        );
        metricRows.addAll(fillCallMetricRows(callRows, orgId, visibleOwnerUserIds));
        metricRows.addAll(logSqlQuery(
                "listWechatRows",
                "startTime=" + startTime + ", endTime=" + endTime + ", orgId=" + orgId,
                () -> employeeStatAnalysisMapper.listWechatRows(startTime, endTime, orgId, visibleOwnerUserIds)
        ));
        return metricRows;
    }

    private List<EmployeeFollowAnalysisMetricRow> fillCallMetricRows(List<EmployeeFollowAnalysisMetricRow> callRows,
                                                                     String orgId,
                                                                     List<String> visibleOwnerUserIds) {
        if (callRows == null || callRows.isEmpty()) {
            return List.of();
        }
        List<EmployeeFollowAnalysisMetricRow> result = new ArrayList<>(callRows.size());
        for (EmployeeFollowAnalysisMetricRow row : callRows) {
            JsonNode bizExtInfo = parseBizExtInfo(row.getBizExtInfo());
            if (bizExtInfo == null) {
                continue;
            }
            row.setOrganizationId(readBizExtText(bizExtInfo, "organization_id", "organizationId"));
            row.setOperatorUserId(readBizExtText(bizExtInfo, "operator_user_id", "operatorUserId"));
            row.setCustomerSource(readBizExtText(bizExtInfo, "customer_source", "customerSource"));
            if (StringUtils.isAnyBlank(row.getOrganizationId(), row.getOperatorUserId())) {
                continue;
            }
            if (!StringUtils.equals(row.getOrganizationId(), orgId)) {
                continue;
            }
            if (visibleOwnerUserIds != null && !visibleOwnerUserIds.contains(row.getOperatorUserId())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private AggregationContext buildAggregationContext(String orgId, String userId, String departmentId) {
        AggregationContext context = new AggregationContext();
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(userId, orgId, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = filterEmployeesByDepartment(
                loadVisibleEmployees(orgId, userId, permission),
                orgId,
                departmentId
        );
        Map<String, EmployeeFollowAnalysisEmployeeDimensionRow> employeeMap = new LinkedHashMap<>();
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            employeeMap.put(item.getOperatorUserId(), item);
        }
        context.setEmployeeMap(employeeMap);
        context.setSqlFilterOperatorUserIds(Boolean.TRUE.equals(permission.getAll()) && StringUtils.isBlank(departmentId) ? null : new ArrayList<>(employeeMap.keySet()));
        Map<String, Integer> employeeOrderMap = new LinkedHashMap<>();
        for (int i = 0; i < employees.size(); i++) {
            employeeOrderMap.put(employees.get(i).getOperatorUserId(), i);
        }
        context.setEmployeeOrderMap(employeeOrderMap);

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

        return context;
    }

    private List<EmployeeFollowAnalysisEmployeeDimensionRow> loadVisibleEmployees(String orgId,
                                                                                  String userId,
                                                                                  DeptDataPermissionDTO permission) {
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = logSqlQuery(
                "listCurrentEmployees",
                "orgId=" + orgId + ", userId=" + userId,
                () -> employeeStatAnalysisMapper.listCurrentEmployees(orgId)
        );
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<String, UserDeptDTO> userDeptMap = logMapQuery(
                "getUserDeptMapByUserIds",
                "orgId=" + orgId + ", userCount=" + employees.size(),
                () -> baseService.getUserDeptMapByUserIds(
                        employees.stream().map(EmployeeFollowAnalysisEmployeeDimensionRow::getOperatorUserId).toList(),
                        orgId
                )
        );
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            UserDeptDTO userDeptDTO = userDeptMap.get(item.getOperatorUserId());
            if (userDeptDTO != null) {
                item.setDepartmentId(userDeptDTO.getDeptId());
                item.setDepartmentName(userDeptDTO.getDeptName());
            }
        }
        if (Boolean.TRUE.equals(permission.getAll())) {
            return employees;
        }
        List<EmployeeFollowAnalysisEmployeeDimensionRow> visibleEmployees = new ArrayList<>();
        if (Boolean.TRUE.equals(permission.getSelf())) {
            for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
                if (StringUtils.equals(item.getOperatorUserId(), userId)) {
                    visibleEmployees.add(item);
                }
            }
            return visibleEmployees;
        }
        Set<String> deptIds = permission.getDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
                if (StringUtils.equals(item.getOperatorUserId(), userId)) {
                    visibleEmployees.add(item);
                }
            }
            return visibleEmployees;
        }
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            if (StringUtils.equals(item.getOperatorUserId(), userId) || deptIds.contains(item.getDepartmentId())) {
                visibleEmployees.add(item);
            }
        }
        return visibleEmployees;
    }

    private List<EmployeeFollowAnalysisEmployeeDimensionRow> filterEmployeesByDepartment(List<EmployeeFollowAnalysisEmployeeDimensionRow> employees,
                                                                                         String orgId,
                                                                                         String departmentId) {
        if (StringUtils.isBlank(departmentId) || employees.isEmpty()) {
            return employees;
        }
        Set<String> departmentIds = loadDepartmentIdsWithChildren(orgId, departmentId);
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        List<EmployeeFollowAnalysisEmployeeDimensionRow> filteredEmployees = new ArrayList<>();
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            if (departmentIds.contains(item.getDepartmentId())) {
                filteredEmployees.add(item);
            }
        }
        return filteredEmployees;
    }

    private Set<String> loadDepartmentIdsWithChildren(String orgId, String departmentId) {
        List<BaseTreeNode> tree = departmentService.getTree(orgId);
        return new LinkedHashSet<>(dataScopeService.getDeptIdsWithChild(tree, Set.of(departmentId)));
    }

    private Map<String, SummaryAccumulator> aggregateHistoryDayRows(List<EmployeeStatDay> rows,
                                                                    EmployeeFollowAnalysisDimensionType dimensionType) {
        Map<String, SummaryAccumulator> result = new LinkedHashMap<>();
        for (EmployeeStatDay row : rows) {
            DimensionValue dimension = resolveDayDimensionValue(row, dimensionType);
            SummaryAccumulator accumulator = result.computeIfAbsent(dimension.getKey(),
                    ignore -> new SummaryAccumulator(dimension.getKey(), defaultText(dimension.getLabel())));
            accumulator.setInboundCustomerBaseCount(accumulator.getInboundCustomerBaseCount() + defaultInt(row.getInboundCustomerCount()));
            accumulator.setContactedCustomerBaseCount(accumulator.getContactedCustomerBaseCount() + defaultInt(row.getContactedCustomerCount()));
            accumulator.setWechatCustomerBaseCount(accumulator.getWechatCustomerBaseCount() + defaultInt(row.getNewWechatFriendCount()));
            accumulator.setDialCount(accumulator.getDialCount() + defaultInt(row.getDialCount()));
            accumulator.setConnectedCount(accumulator.getConnectedCount() + defaultInt(row.getConnectedCount()));
            accumulator.setCallOver1MinCount(accumulator.getCallOver1MinCount() + defaultInt(row.getCallOver1minCount()));
            accumulator.setCallOver3MinCount(accumulator.getCallOver3MinCount() + defaultInt(row.getCallOver3minCount()));
            accumulator.setCallDurationSec(accumulator.getCallDurationSec() + defaultLong(row.getCallDurationSec()));
        }
        return result;
    }

    private DimensionValue resolveDayDimensionValue(EmployeeStatDay row,
                                                    EmployeeFollowAnalysisDimensionType dimensionType) {
        return switch (dimensionType) {
            case EMPLOYEE_NAME -> new DimensionValue(StringUtils.defaultString(row.getOwnerUserId()), defaultText(row.getOwnerUserName()));
            case EMPLOYEE_DEPT -> new DimensionValue(StringUtils.defaultString(row.getOwnerDeptId()), defaultText(row.getOwnerDeptName()));
            case STAT_DAY -> new DimensionValue(StringUtils.defaultString(row.getStatDate()), defaultText(row.getStatDate()));
            case STAT_MONTH -> new DimensionValue(row.getStatDate().substring(0, 7), row.getStatDate().substring(0, 7));
            case CUSTOMER_SOURCE -> throw new IllegalStateException("customerSource should not use history day rows");
        };
    }

    private Map<String, SummaryAccumulator> aggregateRows(List<EmployeeFollowAnalysisMetricRow> metricRows,
                                                          EmployeeFollowAnalysisDimensionType dimensionType,
                                                          AggregationContext context) {
        Map<String, SummaryAccumulator> result = new LinkedHashMap<>();
        for (EmployeeFollowAnalysisMetricRow row : metricRows) {
            DimensionValue dimension = resolveDimensionValue(row, dimensionType, context);
            SummaryAccumulator accumulator = result.computeIfAbsent(dimension.getKey(),
                    ignore -> new SummaryAccumulator(dimension.getKey(), dimension.getLabel()));
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

    private void mergeAccumulatorMaps(Map<String, SummaryAccumulator> target,
                                      Map<String, SummaryAccumulator> source) {
        for (SummaryAccumulator sourceItem : source.values()) {
            SummaryAccumulator targetItem = target.computeIfAbsent(sourceItem.getDimensionKey(),
                    ignore -> new SummaryAccumulator(sourceItem.getDimensionKey(), sourceItem.getDimensionLabel()));
            targetItem.setInboundCustomerBaseCount(targetItem.getInboundCustomerBaseCount() + sourceItem.getInboundCustomerBaseCount());
            targetItem.setContactedCustomerBaseCount(targetItem.getContactedCustomerBaseCount() + sourceItem.getContactedCustomerBaseCount());
            targetItem.setWechatCustomerBaseCount(targetItem.getWechatCustomerBaseCount() + sourceItem.getWechatCustomerBaseCount());
            targetItem.getInboundCustomers().addAll(sourceItem.getInboundCustomers());
            targetItem.getContactedCustomers().addAll(sourceItem.getContactedCustomers());
            targetItem.getWechatCustomers().addAll(sourceItem.getWechatCustomers());
            targetItem.setDialCount(targetItem.getDialCount() + sourceItem.getDialCount());
            targetItem.setConnectedCount(targetItem.getConnectedCount() + sourceItem.getConnectedCount());
            targetItem.setCallOver1MinCount(targetItem.getCallOver1MinCount() + sourceItem.getCallOver1MinCount());
            targetItem.setCallOver3MinCount(targetItem.getCallOver3MinCount() + sourceItem.getCallOver3MinCount());
            targetItem.setCallDurationSec(targetItem.getCallDurationSec() + sourceItem.getCallDurationSec());
        }
    }

    private void fillEmptyDimensions(Map<String, SummaryAccumulator> accumulatorMap,
                                     EmployeeFollowAnalysisDimensionType dimensionType,
                                     AggregationContext context,
                                     QueryRange range) {
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
                // customerSource 是自由文本快照，不存在固定全集，这里不补无数据项。
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
            item.setInboundCustomerCount(accumulator.getInboundCustomerBaseCount() + accumulator.getInboundCustomers().size());
            item.setContactedCustomerCount(accumulator.getContactedCustomerBaseCount() + accumulator.getContactedCustomers().size());
            item.setNewWechatFriendCount(accumulator.getWechatCustomerBaseCount() + accumulator.getWechatCustomers().size());
            item.setDialCount(accumulator.getDialCount());
            item.setConnectedCount(accumulator.getConnectedCount());
            item.setCallOver1MinCount(accumulator.getCallOver1MinCount());
            item.setCallOver3MinCount(accumulator.getCallOver3MinCount());
            item.setCallDurationSec(accumulator.getCallDurationSec());
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
            case CUSTOMER_SOURCE -> Comparator.comparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionLabel, String::compareTo);
            case STAT_DAY, STAT_MONTH -> Comparator.comparing(EmployeeFollowAnalysisSummaryItemResponse::getDimensionKey, String::compareTo);
        };
        responses.sort(comparator);
    }

    private String resolveDisplayLabel(SummaryAccumulator accumulator,
                                       EmployeeFollowAnalysisDimensionType dimensionType,
                                       AggregationContext context) {
        return switch (dimensionType) {
            case EMPLOYEE_NAME -> {
                EmployeeFollowAnalysisEmployeeDimensionRow employee = context.getEmployeeMap().get(accumulator.getDimensionKey());
                yield defaultText(employee == null ? accumulator.getDimensionLabel() : employee.getEmployeeName());
            }
            case EMPLOYEE_DEPT, STAT_DAY, STAT_MONTH -> accumulator.getDimensionLabel();
            case CUSTOMER_SOURCE -> defaultText(accumulator.getDimensionLabel());
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
                String sourceValue = StringUtils.defaultString(row.getCustomerSource());
                yield new DimensionValue(sourceValue, defaultText(sourceValue));
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

    private JsonNode parseBizExtInfo(String bizExtInfo) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return null;
        }
        try {
            return JSON.parseObject(bizExtInfo, JsonNode.class);
        } catch (Exception e) {
            log.warn("员工跟进分析通话汇总 bizExtInfo 解析失败 bizExtInfo={}", bizExtInfo, e);
            return null;
        }
    }

    private String readBizExtText(JsonNode bizExtInfo, String primaryField, String fallbackField) {
        if (bizExtInfo == null) {
            return null;
        }
        String value = StringUtils.trimToNull(bizExtInfo.path(primaryField).asText(null));
        if (value != null) {
            return value;
        }
        return StringUtils.trimToNull(bizExtInfo.path(fallbackField).asText(null));
    }

    private int orderOf(Map<String, Integer> orderMap, String key) {
        return orderMap.getOrDefault(StringUtils.defaultString(key), Integer.MAX_VALUE);
    }

    private <T> List<T> logSqlQuery(String sqlName, String params, Supplier<List<T>> supplier) {
        long start = System.currentTimeMillis();
        //log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        List<T> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        //log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }

    private <K, V> Map<K, V> logMapQuery(String sqlName, String params, Supplier<Map<K, V>> supplier) {
        long start = System.currentTimeMillis();
        Map<K, V> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        //log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
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
        private List<String> sqlFilterOperatorUserIds;
        private Map<String, Integer> employeeOrderMap = new LinkedHashMap<>();
        private Map<String, String> departmentMap = new LinkedHashMap<>();
        private Map<String, Integer> departmentOrderMap = new LinkedHashMap<>();
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
        private int inboundCustomerBaseCount;
        private int contactedCustomerBaseCount;
        private int wechatCustomerBaseCount;
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

        void addAll(UniqueCustomerSet source) {
            customerIds.addAll(source.customerIds);
        }
    }
}
