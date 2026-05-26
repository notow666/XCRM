package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeStatDay;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisRebuildRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeStatAnalysisMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class EmployeeStatDayBuildService {

    private static final int BATCH_SIZE = 500;

    @Resource
    private EmployeeStatAnalysisMapper employeeStatAnalysisMapper;
    @Resource
    private BaseMapper<EmployeeStatDay> employeeStatDayBaseMapper;

    public void rebuildRange(EmployeeFollowAnalysisRebuildRequest request, String operatorUserId) {
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            rebuildDay(cursor, operatorUserId);
            cursor = cursor.plusDays(1);
        }
    }

    public void rebuildDay(LocalDate statDate, String operatorUserId) {
        String statDateValue = statDate.toString();
        logSqlCommand("deleteStatDay", "statDate=" + statDateValue, () -> deleteByStatDate(statDateValue));
        long startTime = statDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTime = statDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
        List<EmployeeFollowAnalysisMetricRow> callRows = logSqlQuery("listCallDayRows", "startTime=" + startTime + ", endTime=" + endTime,
                () -> employeeStatAnalysisMapper.listCallDayRows(startTime, endTime));
        List<EmployeeStatDay> mergedRows = mergeRows(List.of(
                logSqlQuery("listEventDayRows", "statDate=" + statDateValue,
                        () -> employeeStatAnalysisMapper.listEventDayRows(statDateValue)),
                buildCallDayRows(callRows)
        ));
        if (CollectionUtils.isEmpty(mergedRows)) {
            return;
        }
        long now = System.currentTimeMillis();
        List<EmployeeStatDay> insertList = new ArrayList<>(mergedRows.size());
        for (EmployeeStatDay row : mergedRows) {
            EmployeeStatDay item = new EmployeeStatDay();
            item.setId(IDGenerator.nextStr());
            item.setOrganizationId(row.getOrganizationId());
            item.setStatDate(row.getStatDate());
            item.setOwnerUserId(row.getOwnerUserId());
            item.setOwnerUserName(row.getOwnerUserName());
            item.setOwnerDeptId(row.getOwnerDeptId());
            item.setOwnerDeptName(row.getOwnerDeptName());
            item.setInboundCustomerCount(defaultInt(row.getInboundCustomerCount()));
            item.setContactedCustomerCount(defaultInt(row.getContactedCustomerCount()));
            item.setDialCount(defaultInt(row.getDialCount()));
            item.setConnectedCount(defaultInt(row.getConnectedCount()));
            item.setCallDurationSec(defaultLong(row.getCallDurationSec()));
            item.setCallOver1minCount(defaultInt(row.getCallOver1minCount()));
            item.setCallOver3minCount(defaultInt(row.getCallOver3minCount()));
            item.setNewWechatFriendCount(defaultInt(row.getNewWechatFriendCount()));
            item.setCreateUser(operatorUserId);
            item.setCreateTime(now);
            insertList.add(item);
        }
        for (int i = 0; i < insertList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, insertList.size());
            int batchStart = i;
            int batchEnd = end;
            logSqlCommand("batchInsertStatDay",
                    "statDate=" + statDateValue + ", batchStart=" + batchStart + ", batchEnd=" + batchEnd
                            + ", batchSize=" + (batchEnd - batchStart),
                    () -> employeeStatDayBaseMapper.batchInsert(insertList.subList(batchStart, batchEnd)));
        }
        log.info("员工分析日报重算完成，statDate={}, count={}", statDateValue, insertList.size());
    }

    private void deleteByStatDate(String statDate) {
        LambdaQueryWrapper<EmployeeStatDay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmployeeStatDay::getStatDate, statDate);
        employeeStatDayBaseMapper.deleteByLambda(wrapper);
    }

    private List<EmployeeStatDay> mergeRows(List<List<EmployeeStatDay>> groups) {
        Map<String, EmployeeStatDay> resultMap = new LinkedHashMap<>();
        for (List<EmployeeStatDay> rows : groups) {
            if (CollectionUtils.isEmpty(rows)) {
                continue;
            }
            for (EmployeeStatDay row : rows) {
                if (StringUtils.isAnyBlank(row.getOrganizationId(), row.getStatDate(), row.getOwnerUserId())) {
                    continue;
                }
                String key = buildKey(row.getOrganizationId(), row.getStatDate(), row.getOwnerUserId());
                EmployeeStatDay target = resultMap.get(key);
                if (target == null) {
                    resultMap.put(key, copyRow(row));
                    continue;
                }
                target.setOwnerUserName(defaultString(target.getOwnerUserName(), row.getOwnerUserName()));
                target.setOwnerDeptId(defaultString(target.getOwnerDeptId(), row.getOwnerDeptId()));
                target.setOwnerDeptName(defaultString(target.getOwnerDeptName(), row.getOwnerDeptName()));
                target.setInboundCustomerCount(defaultInt(target.getInboundCustomerCount()) + defaultInt(row.getInboundCustomerCount()));
                target.setContactedCustomerCount(defaultInt(target.getContactedCustomerCount()) + defaultInt(row.getContactedCustomerCount()));
                target.setDialCount(defaultInt(target.getDialCount()) + defaultInt(row.getDialCount()));
                target.setConnectedCount(defaultInt(target.getConnectedCount()) + defaultInt(row.getConnectedCount()));
                target.setCallDurationSec(defaultLong(target.getCallDurationSec()) + defaultLong(row.getCallDurationSec()));
                target.setCallOver1minCount(defaultInt(target.getCallOver1minCount()) + defaultInt(row.getCallOver1minCount()));
                target.setCallOver3minCount(defaultInt(target.getCallOver3minCount()) + defaultInt(row.getCallOver3minCount()));
                target.setNewWechatFriendCount(defaultInt(target.getNewWechatFriendCount()) + defaultInt(row.getNewWechatFriendCount()));
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    private EmployeeStatDay copyRow(EmployeeStatDay source) {
        EmployeeStatDay target = new EmployeeStatDay();
        target.setOrganizationId(source.getOrganizationId());
        target.setStatDate(source.getStatDate());
        target.setOwnerUserId(source.getOwnerUserId());
        target.setOwnerUserName(source.getOwnerUserName());
        target.setOwnerDeptId(source.getOwnerDeptId());
        target.setOwnerDeptName(source.getOwnerDeptName());
        target.setInboundCustomerCount(defaultInt(source.getInboundCustomerCount()));
        target.setContactedCustomerCount(defaultInt(source.getContactedCustomerCount()));
        target.setDialCount(defaultInt(source.getDialCount()));
        target.setConnectedCount(defaultInt(source.getConnectedCount()));
        target.setCallDurationSec(defaultLong(source.getCallDurationSec()));
        target.setCallOver1minCount(defaultInt(source.getCallOver1minCount()));
        target.setCallOver3minCount(defaultInt(source.getCallOver3minCount()));
        target.setNewWechatFriendCount(defaultInt(source.getNewWechatFriendCount()));
        return target;
    }

    private List<EmployeeStatDay> buildCallDayRows(List<EmployeeFollowAnalysisMetricRow> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return List.of();
        }
        Map<String, EmployeeStatDay> resultMap = new LinkedHashMap<>();
        for (EmployeeFollowAnalysisMetricRow row : rows) {
            JsonNode bizExtInfo = parseBizExtInfo(row.getBizExtInfo());
            if (bizExtInfo == null) {
                continue;
            }
            String organizationId = readBizExtText(bizExtInfo, "organization_id", "organizationId");
            String operatorUserId = readBizExtText(bizExtInfo, "operator_user_id", "operatorUserId");
            if (StringUtils.isAnyBlank(organizationId, row.getStatDate(), operatorUserId)) {
                continue;
            }
            String key = buildKey(organizationId, row.getStatDate(), operatorUserId);
            EmployeeStatDay item = resultMap.computeIfAbsent(key, ignore -> {
                EmployeeStatDay day = new EmployeeStatDay();
                day.setOrganizationId(organizationId);
                day.setStatDate(row.getStatDate());
                day.setOwnerUserId(operatorUserId);
                day.setOwnerUserName(readBizExtText(bizExtInfo, "operator_user_name", "operatorUserName"));
                day.setOwnerDeptId(readBizExtText(bizExtInfo, "operator_dept_id", "operatorDeptId"));
                day.setOwnerDeptName(readBizExtText(bizExtInfo, "operator_dept_name", "operatorDeptName"));
                return day;
            });
            item.setDialCount(defaultInt(item.getDialCount()) + defaultInt(row.getDialCount()));
            item.setConnectedCount(defaultInt(item.getConnectedCount()) + defaultInt(row.getConnectedCount()));
            item.setCallDurationSec(defaultLong(item.getCallDurationSec()) + defaultLong(row.getCallDurationSec()));
            item.setCallOver1minCount(defaultInt(item.getCallOver1minCount()) + defaultInt(row.getCallOver1minCount()));
            item.setCallOver3minCount(defaultInt(item.getCallOver3minCount()) + defaultInt(row.getCallOver3minCount()));
        }
        return new ArrayList<>(resultMap.values());
    }

    private String defaultString(String currentValue, String candidateValue) {
        return StringUtils.isNotBlank(currentValue) ? currentValue : candidateValue;
    }

    private String buildKey(String organizationId, String statDate, String ownerUserId) {
        return organizationId + "|" + statDate + "|" + ownerUserId;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private JsonNode parseBizExtInfo(String bizExtInfo) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return null;
        }
        try {
            return JSON.parseObject(bizExtInfo, JsonNode.class);
        } catch (Exception e) {
            log.warn("员工分析日报通话快照 bizExtInfo 解析失败 bizExtInfo={}", bizExtInfo, e);
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

    private <T> List<T> logSqlQuery(String sqlName, String params, Supplier<List<T>> supplier) {
        long start = System.currentTimeMillis();
        List<T> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        log.info("员工分析日报SQL结束, sqlName={}, params={}, costMs={}, resultSize={}",
                sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }

    private void logSqlCommand(String sqlName, String params, Runnable runnable) {
        long start = System.currentTimeMillis();
        runnable.run();
        long cost = System.currentTimeMillis() - start;
        log.info("员工分析日报SQL结束, sqlName={}, params={}, costMs={}", sqlName, params, cost);
    }
}
