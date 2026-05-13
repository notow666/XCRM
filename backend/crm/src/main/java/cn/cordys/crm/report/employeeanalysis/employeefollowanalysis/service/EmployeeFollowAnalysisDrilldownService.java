package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisMetricType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeFollowAnalysisTimePreset;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeFollowAnalysisMapper;
import cn.cordys.crm.system.dto.field.base.OptionProp;
import cn.cordys.crm.system.service.ModuleFieldExtService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class, readOnly = true)
public class EmployeeFollowAnalysisDrilldownService {

    @Resource
    private EmployeeFollowAnalysisMapper employeeFollowAnalysisMapper;
    @Resource
    private ModuleFieldExtService moduleFieldExtService;

    public Pager<List<EmployeeFollowAnalysisDrilldownItemResponse>> drilldown(EmployeeFollowAnalysisDrilldownRequest request, String orgId) {
        // 下钻不查事实表，直接按当前口径回查原始业务/MMBA 明细，避免汇总和明细脱节。
        fillTimeRange(request);
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        EmployeeFollowAnalysisMetricType metricType = EmployeeFollowAnalysisMetricType.fromValue(request.getMetricType());
        List<EmployeeFollowAnalysisDrilldownItemResponse> list = switch (metricType) {
            case INBOUND_CUSTOMER -> employeeFollowAnalysisMapper.listInboundCustomerDrilldown(request, orgId);
            case CONTACTED_CUSTOMER -> employeeFollowAnalysisMapper.listContactedCustomerDrilldown(request, orgId);
            case NEW_WECHAT_FRIEND -> employeeFollowAnalysisMapper.listWechatFriendDrilldown(request, orgId);
            case DIAL_COUNT, CONNECTED_COUNT, CALL_OVER_1MIN, CALL_OVER_3MIN ->
                    employeeFollowAnalysisMapper.listCallDrilldown(request, orgId);
        };
        fillCustomerSourceLabels(list, orgId);
        return PageUtils.setPageInfo(page, list);
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
        List<OptionProp> options = moduleFieldExtService.getFieldOptions(FormKey.CUSTOMER.getKey(), orgId, "customerSource");
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
}
