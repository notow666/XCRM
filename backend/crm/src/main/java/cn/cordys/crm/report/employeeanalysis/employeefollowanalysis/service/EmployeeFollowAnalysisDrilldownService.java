package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.common.util.PhoneMaskUtil;
import cn.cordys.crm.system.service.GlobalPhoneMaskConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.BaseTreeNode;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.util.JSON;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisMetricType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeStatAnalysisMapper;
import cn.cordys.crm.system.service.DepartmentService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Transactional(rollbackFor = Exception.class, readOnly = true)
@Slf4j
public class EmployeeFollowAnalysisDrilldownService {

    @Resource
    private EmployeeStatAnalysisMapper employeeStatAnalysisMapper;
    @Resource
    private BaseService baseService;
    @Resource
    private DataScopeService dataScopeService;

    @Resource
    private GlobalPhoneMaskConfigService globalPhoneMaskConfigService;
    @Resource
    private DepartmentService departmentService;

    public Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldown(EmployeeFollowAnalysisDrilldownRequest request, String orgId, String userId) {
        // 下钻不查日报汇总表，直接按当前口径回查原始业务/MMBA 明细，避免汇总和明细脱节。
        fillTimeRange(request);
        // 下钻和汇总复用同一数据权限锚点，保证“谁能看汇总，谁就只能看同范围的明细”。
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(userId, orgId, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        List<String> visibleOperatorUserIds = loadDrilldownOperatorUserIds(orgId, userId, permission, request.getDepartmentId());
        if (visibleOperatorUserIds != null && visibleOperatorUserIds.isEmpty()) {
            Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
            return PageUtils.setPageInfo(page, List.of());
        }
        EmployeeFollowAnalysisMetricType metricType = EmployeeFollowAnalysisMetricType.fromValue(request.getMetricType());
        if (isCallMetric(metricType) && StringUtils.equals(request.getDimensionType(), "customerSource")) {
            return drilldownCallByCustomerSource(request, orgId, visibleOperatorUserIds);
        }
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<EmployeeFollowAnalysisDrilldownItemResponse> list = switch (metricType) {
            case INBOUND_CUSTOMER -> logSqlQuery(
                    "listInboundCustomerDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                            () -> employeeStatAnalysisMapper.listInboundCustomerDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case CONTACTED_CUSTOMER -> logSqlQuery(
                    "listContactedCustomerDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                    () -> employeeStatAnalysisMapper.listContactedCustomerDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case NEW_WECHAT_FRIEND -> logSqlQuery(
                    "listWechatFriendDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                    () -> employeeStatAnalysisMapper.listWechatFriendDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case DIAL_COUNT, CONNECTED_COUNT, CALL_OVER_1MIN, CALL_OVER_3MIN ->
                    logSqlQuery(
                            "listCallDrilldown",
                            "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                                    + ", dimensionKey=" + request.getDimensionKey(),
                            () -> employeeStatAnalysisMapper.listCallDrilldown(request, orgId, visibleOperatorUserIds)
                    );
        };
        if (isCallMetric(metricType)) {
            fillCallSnapshotFields(list);
        }
        applyGlobalPhoneMask(list, orgId);
        fillBlankCustomerSource(list);
        return PageUtils.setPageInfo(page, list);
    }

    private Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldownCallByCustomerSource(EmployeeFollowAnalysisDrilldownRequest request,
                                                                                                   String orgId,
                                                                                                   List<String> visibleOperatorUserIds) {
        List<EmployeeFollowAnalysisDrilldownItemResponse> allRows = logSqlQuery(
                "listCallDrilldown",
                "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                        + ", dimensionKey=" + request.getDimensionKey(),
                () -> employeeStatAnalysisMapper.listCallDrilldown(request, orgId, visibleOperatorUserIds)
        );
        fillCallSnapshotFields(allRows);
        List<EmployeeFollowAnalysisDrilldownItemResponse> filteredRows = filterCallRowsByCustomerSource(allRows, request.getDimensionKey());
        applyGlobalPhoneMask(filteredRows, orgId);
        fillBlankCustomerSource(filteredRows);
        return buildPager(filteredRows, request.getCurrent(), request.getPageSize());
    }

    private List<String> loadDrilldownOperatorUserIds(String orgId,
                                                       String userId,
                                                       DeptDataPermissionDTO permission,
                                                       String departmentId) {
        if (Boolean.TRUE.equals(permission.getAll()) && StringUtils.isBlank(departmentId)) {
            return null;
        }
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = loadCurrentEmployeesWithDepartment(orgId);
        if (employees.isEmpty()) {
            return List.of();
        }
        List<EmployeeFollowAnalysisEmployeeDimensionRow> visibleEmployees = filterEmployeesByPermission(employees, userId, permission);
        visibleEmployees = filterEmployeesByDepartment(visibleEmployees, orgId, departmentId);
        List<String> visibleOperatorUserIds = new ArrayList<>();
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : visibleEmployees) {
            visibleOperatorUserIds.add(item.getOperatorUserId());
        }
        return visibleOperatorUserIds;
    }

    private List<EmployeeFollowAnalysisEmployeeDimensionRow> loadCurrentEmployeesWithDepartment(String orgId) {
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = logSqlQuery(
                "listCurrentEmployees-drilldown",
                "orgId=" + orgId,
                () -> employeeStatAnalysisMapper.listCurrentEmployees(orgId)
        );
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<String, UserDeptDTO> userDeptMap = logMapQuery(
                "getUserDeptMapByUserIds-drilldown",
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
        return employees;
    }

    private List<EmployeeFollowAnalysisEmployeeDimensionRow> filterEmployeesByPermission(List<EmployeeFollowAnalysisEmployeeDimensionRow> employees,
                                                                                         String userId,
                                                                                         DeptDataPermissionDTO permission) {
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

    private void fillTimeRange(EmployeeFollowAnalysisDrilldownRequest request) {
        EmployeeFollowAnalysisTimePreset timePreset = EmployeeFollowAnalysisTimePreset.fromValue(request.getTimePreset());
        LocalDate today = LocalDate.now();
        switch (timePreset) {
            case TODAY -> {
                request.setStartTime(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                request.setEndTime(System.currentTimeMillis());
            }
            case YESTERDAY -> {
                LocalDate yesterday = today.minusDays(1);
                request.setStartTime(yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                request.setEndTime(yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1);
            }
            case WEEK -> {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                request.setStartTime(start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                request.setEndTime(System.currentTimeMillis());
            }
            case MONTH -> {
                LocalDate start = today.withDayOfMonth(1);
                request.setStartTime(start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                request.setEndTime(System.currentTimeMillis());
            }
            case CUSTOM -> {
                if (request.getStartTime() == null || request.getEndTime() == null) {
                    throw new IllegalArgumentException("custom range timestamp is required");
                }
                if (request.getStartTime() > request.getEndTime()) {
                    throw new IllegalArgumentException("startTime cannot be after endTime");
                }
                // 自定义区间在下钻层统一扩成整天边界，和汇总查询的日期语义保持一致。
                LocalDate endDate = Instant.ofEpochMilli(request.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                request.setStartTime(Instant.ofEpochMilli(request.getStartTime()).atZone(ZoneId.systemDefault()).toLocalDate()
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                request.setEndTime(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1);
            }
        }
    }

    private void fillBlankCustomerSource(List<EmployeeFollowAnalysisDrilldownItemResponse> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (EmployeeFollowAnalysisDrilldownItemResponse item : list) {
            if (StringUtils.isBlank(item.getCustomerSource())) {
                item.setCustomerSource("-");
            }
        }
    }

    private void fillCallSnapshotFields(List<EmployeeFollowAnalysisDrilldownItemResponse> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (EmployeeFollowAnalysisDrilldownItemResponse item : list) {
            JsonNode bizExtInfo = parseBizExtInfo(item.getBizExtInfo());
            if (bizExtInfo == null) {
                continue;
            }
            if (StringUtils.isBlank(item.getCustomerName())) {
                item.setCustomerName(readBizExtText(bizExtInfo, "customer_name", "customerName"));
            }
            if (StringUtils.isBlank(item.getMobile())) {
                item.setMobile(readBizExtText(bizExtInfo, "customer_mobile", "customerMobile"));
            }
            item.setOwnerName(defaultIfBlank(item.getOwnerName(), readBizExtText(bizExtInfo, "owner_user_name", "ownerUserName")));
            item.setDepartmentName(defaultIfBlank(item.getDepartmentName(), readBizExtText(bizExtInfo, "owner_dept_name", "ownerDeptName")));
            item.setCustomerSource(defaultIfBlank(item.getCustomerSource(), readBizExtText(bizExtInfo, "customer_source", "customerSource")));
        }
    }

    private List<EmployeeFollowAnalysisDrilldownItemResponse> filterCallRowsByCustomerSource(List<EmployeeFollowAnalysisDrilldownItemResponse> rows,
                                                                                              String dimensionKey) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<EmployeeFollowAnalysisDrilldownItemResponse> filteredRows = new ArrayList<>();
        boolean emptySource = StringUtils.equals(dimensionKey, "__EMPTY__");
        for (EmployeeFollowAnalysisDrilldownItemResponse row : rows) {
            String customerSource = StringUtils.defaultString(row.getCustomerSource());
            if (emptySource) {
                if (StringUtils.isBlank(customerSource)) {
                    filteredRows.add(row);
                }
                continue;
            }
            if (StringUtils.equals(customerSource, dimensionKey)) {
                filteredRows.add(row);
            }
        }
        return filteredRows;
    }

    private Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> buildPager(List<EmployeeFollowAnalysisDrilldownItemResponse> rows,
                                                                                Integer current,
                                                                                Integer pageSize) {
        int actualCurrent = current == null || current <= 0 ? 1 : current;
        int actualPageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        int total = rows == null ? 0 : rows.size();
        int fromIndex = Math.min((actualCurrent - 1) * actualPageSize, total);
        int toIndex = Math.min(fromIndex + actualPageSize, total);
        List<EmployeeFollowAnalysisDrilldownItemResponse> pageRows = total == 0 ? List.of() : rows.subList(fromIndex, toIndex);
        return new Pager<>(pageRows, total, actualPageSize, actualCurrent);
    }

    private boolean isCallMetric(EmployeeFollowAnalysisMetricType metricType) {
        return metricType == EmployeeFollowAnalysisMetricType.DIAL_COUNT
                || metricType == EmployeeFollowAnalysisMetricType.CONNECTED_COUNT
                || metricType == EmployeeFollowAnalysisMetricType.CALL_OVER_1MIN
                || metricType == EmployeeFollowAnalysisMetricType.CALL_OVER_3MIN;
    }

    private JsonNode parseBizExtInfo(String bizExtInfo) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return null;
        }
        try {
            return JSON.parseObject(bizExtInfo, JsonNode.class);
        } catch (Exception e) {
            log.warn("员工跟进分析通话下钻 bizExtInfo 解析失败 bizExtInfo={}", bizExtInfo, e);
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

    private String defaultIfBlank(String currentValue, String fallbackValue) {
        return StringUtils.isBlank(currentValue) ? fallbackValue : currentValue;
    }

    private <T> List<T> logSqlQuery(String sqlName, String params, Supplier<List<T>> supplier) {
        long start = System.currentTimeMillis();
        log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        List<T> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }

    private <K, V> Map<K, V> logMapQuery(String sqlName, String params, Supplier<Map<K, V>> supplier) {
        long start = System.currentTimeMillis();
        log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        Map<K, V> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }

    private void applyGlobalPhoneMask(List<EmployeeFollowAnalysisDrilldownItemResponse> list, String orgId) {
        if (list == null || list.isEmpty()) {
            return;
        }
        if (!globalPhoneMaskConfigService.isEnabled(orgId)) {
            return;
        }
        for (EmployeeFollowAnalysisDrilldownItemResponse item : list) {
            item.setMobile(PhoneMaskUtil.maskGlobalPhone(item.getMobile()));
        }
    }
}
