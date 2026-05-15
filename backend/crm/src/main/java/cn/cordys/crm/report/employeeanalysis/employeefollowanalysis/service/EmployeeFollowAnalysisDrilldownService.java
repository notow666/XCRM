package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisMetricType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeFollowAnalysisMapper;
import cn.cordys.crm.system.dto.field.base.OptionProp;
import cn.cordys.crm.system.service.ModuleFieldExtService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Transactional(rollbackFor = Exception.class, readOnly = true)
@Slf4j
public class EmployeeFollowAnalysisDrilldownService {

    @Resource
    private EmployeeFollowAnalysisMapper employeeFollowAnalysisMapper;
    @Resource
    private ModuleFieldExtService moduleFieldExtService;
    @Resource
    private BaseService baseService;
    @Resource
    private DataScopeService dataScopeService;

    public Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldown(EmployeeFollowAnalysisDrilldownRequest request, String orgId, String userId) {
        // 下钻不查事实表，直接按当前口径回查原始业务/MMBA 明细，避免汇总和明细脱节。
        fillTimeRange(request);
        // 下钻和汇总复用同一数据权限锚点，保证“谁能看汇总，谁就只能看同范围的明细”。
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(userId, orgId, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        List<String> visibleOperatorUserIds = loadDrilldownOperatorUserIds(orgId, userId, permission);
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        if (!Boolean.TRUE.equals(permission.getAll()) && visibleOperatorUserIds.isEmpty()) {
            return PageUtils.setPageInfo(page, List.of());
        }
        EmployeeFollowAnalysisMetricType metricType = EmployeeFollowAnalysisMetricType.fromValue(request.getMetricType());
        List<EmployeeFollowAnalysisDrilldownItemResponse> list = switch (metricType) {
            case INBOUND_CUSTOMER -> logSqlQuery(
                    "listInboundCustomerDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                    () -> employeeFollowAnalysisMapper.listInboundCustomerDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case CONTACTED_CUSTOMER -> logSqlQuery(
                    "listContactedCustomerDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                    () -> employeeFollowAnalysisMapper.listContactedCustomerDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case NEW_WECHAT_FRIEND -> logSqlQuery(
                    "listWechatFriendDrilldown",
                    "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                            + ", dimensionKey=" + request.getDimensionKey(),
                    () -> employeeFollowAnalysisMapper.listWechatFriendDrilldown(request, orgId, visibleOperatorUserIds)
            );
            case DIAL_COUNT, CONNECTED_COUNT, CALL_OVER_1MIN, CALL_OVER_3MIN ->
                    logSqlQuery(
                            "listCallDrilldown",
                            "orgId=" + orgId + ", metricType=" + request.getMetricType() + ", dimensionType=" + request.getDimensionType()
                                    + ", dimensionKey=" + request.getDimensionKey(),
                            () -> employeeFollowAnalysisMapper.listCallDrilldown(request, orgId, visibleOperatorUserIds)
                    );
        };
        fillCustomerSourceLabels(list, orgId);
        return PageUtils.setPageInfo(page, list);
    }

    private List<String> loadDrilldownOperatorUserIds(String orgId,
                                                       String userId,
                                                       DeptDataPermissionDTO permission) {
        if (Boolean.TRUE.equals(permission.getAll())) {
            return null;
        }
        if (Boolean.TRUE.equals(permission.getSelf())) {
            return List.of(userId);
        }
        Set<String> deptIds = permission.getDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of(userId);
        }
        List<EmployeeFollowAnalysisEmployeeDimensionRow> employees = logSqlQuery(
                "listCurrentEmployees-drilldown",
                "orgId=" + orgId + ", userId=" + userId,
                () -> employeeFollowAnalysisMapper.listCurrentEmployees(orgId)
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
        List<String> visibleOperatorUserIds = new ArrayList<>();
        for (EmployeeFollowAnalysisEmployeeDimensionRow item : employees) {
            UserDeptDTO userDeptDTO = userDeptMap.get(item.getOperatorUserId());
            String departmentId = userDeptDTO == null ? null : userDeptDTO.getDeptId();
            if (StringUtils.equals(item.getOperatorUserId(), userId) || deptIds.contains(departmentId)) {
                visibleOperatorUserIds.add(item.getOperatorUserId());
            }
        }
        return visibleOperatorUserIds;
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

    private void fillCustomerSourceLabels(List<EmployeeFollowAnalysisDrilldownItemResponse> list, String orgId) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 下钻明细里 customerSource 返回的是当前值，这里再翻译成前端展示标签。
        List<OptionProp> options = logSqlQuery(
                "getFieldOptions(customerSource)-drilldown",
                "formKey=" + FormKey.CUSTOMER.getKey() + ", orgId=" + orgId + ", internalKey=customerSource",
                () -> moduleFieldExtService.getFieldOptions(FormKey.CUSTOMER.getKey(), orgId, "customerSource")
        );
        Map<String, String> sourceLabelMap = new LinkedHashMap<>();
        for (OptionProp option : options) {
            sourceLabelMap.put(StringUtils.defaultString(option.getValue()), option.getLabel());
        }
        for (EmployeeFollowAnalysisDrilldownItemResponse item : list) {
            String rawValue = StringUtils.defaultString(item.getCustomerSource());
            if (StringUtils.isBlank(rawValue)) {
                item.setCustomerSource("-");
                continue;
            }
            item.setCustomerSource(sourceLabelMap.getOrDefault(rawValue, rawValue));
        }
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
        //log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        Map<K, V> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        //log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }
}
