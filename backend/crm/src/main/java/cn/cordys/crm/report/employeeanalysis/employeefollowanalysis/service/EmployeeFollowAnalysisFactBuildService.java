package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeFollowAnalysisFactDay;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisRebuildRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeFollowAnalysisMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
public class EmployeeFollowAnalysisFactBuildService {

    private static final int BATCH_SIZE = 500;

    @Resource
    private EmployeeFollowAnalysisMapper employeeFollowAnalysisMapper;
    @Resource
    private BaseMapper<EmployeeFollowAnalysisFactDay> employeeFollowAnalysisFactDayBaseMapper;

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
        // 历史重算按天覆盖写，先删旧事实，再按四类来源重新聚合入表。
        logSqlCommand("deleteByStatDate", "statDate=" + statDate, () -> deleteByStatDate(statDate.toString()));
        long startTime = statDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTime = statDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        List<EmployeeFollowAnalysisMetricRow> mergedRows = mergeMetricRows(List.of(
                logSqlQuery("listInboundRows-rebuild",
                        buildSqlParams(startTime, endTime),
                        () -> employeeFollowAnalysisMapper.listInboundRows(startTime, endTime, null)),
                logSqlQuery("listContactedRows-rebuild",
                        buildSqlParams(startTime, endTime),
                        () -> employeeFollowAnalysisMapper.listContactedRows(startTime, endTime, null)),
                logSqlQuery("listCallRows-rebuild",
                        buildSqlParams(startTime, endTime),
                        () -> employeeFollowAnalysisMapper.listCallRows(startTime, endTime, null)),
                logSqlQuery("listWechatRows-rebuild",
                        buildSqlParams(startTime, endTime),
                        () -> employeeFollowAnalysisMapper.listWechatRows(startTime, endTime, null))
        ));
        if (CollectionUtils.isEmpty(mergedRows)) {
            return;
        }

        long now = System.currentTimeMillis();
        List<EmployeeFollowAnalysisFactDay> insertList = new ArrayList<>(mergedRows.size());
        for (EmployeeFollowAnalysisMetricRow row : mergedRows) {
            EmployeeFollowAnalysisFactDay item = new EmployeeFollowAnalysisFactDay();
            item.setId(IDGenerator.nextStr());
            item.setStatDate(row.getStatDate());
            item.setCustomerId(row.getCustomerId());
            item.setOperatorUserId(row.getOperatorUserId());
            item.setInboundCustomerFlag(defaultInt(row.getInboundCustomerFlag()));
            item.setContactedCustomerFlag(defaultInt(row.getContactedCustomerFlag()));
            item.setNewWechatFriendFlag(defaultInt(row.getNewWechatFriendFlag()));
            item.setDialCount(defaultInt(row.getDialCount()));
            item.setConnectedCount(defaultInt(row.getConnectedCount()));
            item.setCallOver1minCount(defaultInt(row.getCallOver1minCount()));
            item.setCallOver3minCount(defaultInt(row.getCallOver3minCount()));
            item.setCallDurationSec(defaultLong(row.getCallDurationSec()));
            item.setCreateUser(operatorUserId);
            item.setUpdateUser(operatorUserId);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            insertList.add(item);
        }

        for (int i = 0; i < insertList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, insertList.size());
            int batchStart = i;
            int batchEnd = end;
            logSqlCommand(
                    "batchInsertFactDay",
                    "statDate=" + statDate + ", batchStart=" + batchStart + ", batchEnd=" + batchEnd + ", batchSize=" + (batchEnd - batchStart),
                    () -> employeeFollowAnalysisFactDayBaseMapper.batchInsert(insertList.subList(batchStart, batchEnd))
            );
        }
        log.info("员工跟进分析事实日报重算完成，statDate={}, count={}", statDate, insertList.size());
    }

    private void deleteByStatDate(String statDate) {
        LambdaQueryWrapper<EmployeeFollowAnalysisFactDay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmployeeFollowAnalysisFactDay::getStatDate, statDate);
        employeeFollowAnalysisFactDayBaseMapper.deleteByLambda(wrapper);
    }

    private List<EmployeeFollowAnalysisMetricRow> mergeMetricRows(List<List<EmployeeFollowAnalysisMetricRow>> metricGroups) {
        Map<String, EmployeeFollowAnalysisMetricRow> resultMap = new LinkedHashMap<>();
        for (List<EmployeeFollowAnalysisMetricRow> rows : metricGroups) {
            if (CollectionUtils.isEmpty(rows)) {
                continue;
            }
            for (EmployeeFollowAnalysisMetricRow row : rows) {
                String key = buildKey(row.getStatDate(), row.getCustomerId(), row.getOperatorUserId());
                EmployeeFollowAnalysisMetricRow target = resultMap.computeIfAbsent(key, ignore -> newMetricRow(row));
                // flag 型指标取最大值，计数/时长型指标做累加，最终落成 stat_date + customer + operator 的事实粒度。
                target.setInboundCustomerFlag(Math.max(defaultInt(target.getInboundCustomerFlag()), defaultInt(row.getInboundCustomerFlag())));
                target.setContactedCustomerFlag(Math.max(defaultInt(target.getContactedCustomerFlag()), defaultInt(row.getContactedCustomerFlag())));
                target.setNewWechatFriendFlag(Math.max(defaultInt(target.getNewWechatFriendFlag()), defaultInt(row.getNewWechatFriendFlag())));
                target.setDialCount(defaultInt(target.getDialCount()) + defaultInt(row.getDialCount()));
                target.setConnectedCount(defaultInt(target.getConnectedCount()) + defaultInt(row.getConnectedCount()));
                target.setCallOver1minCount(defaultInt(target.getCallOver1minCount()) + defaultInt(row.getCallOver1minCount()));
                target.setCallOver3minCount(defaultInt(target.getCallOver3minCount()) + defaultInt(row.getCallOver3minCount()));
                target.setCallDurationSec(defaultLong(target.getCallDurationSec()) + defaultLong(row.getCallDurationSec()));
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    private EmployeeFollowAnalysisMetricRow newMetricRow(EmployeeFollowAnalysisMetricRow source) {
        EmployeeFollowAnalysisMetricRow target = new EmployeeFollowAnalysisMetricRow();
        target.setStatDate(source.getStatDate());
        target.setCustomerId(source.getCustomerId());
        target.setOperatorUserId(source.getOperatorUserId());
        target.setInboundCustomerFlag(defaultInt(source.getInboundCustomerFlag()));
        target.setContactedCustomerFlag(defaultInt(source.getContactedCustomerFlag()));
        target.setNewWechatFriendFlag(defaultInt(source.getNewWechatFriendFlag()));
        target.setDialCount(defaultInt(source.getDialCount()));
        target.setConnectedCount(defaultInt(source.getConnectedCount()));
        target.setCallOver1minCount(defaultInt(source.getCallOver1minCount()));
        target.setCallOver3minCount(defaultInt(source.getCallOver3minCount()));
        target.setCallDurationSec(defaultLong(source.getCallDurationSec()));
        return target;
    }

    private String buildKey(String statDate, String customerId, String operatorUserId) {
        return statDate + "|" + customerId + "|" + operatorUserId;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String buildSqlParams(long startTime, long endTime) {
        return "startTime=" + startTime + ", endTime=" + endTime + ", orgId=null";
    }

    private <T> List<T> logSqlQuery(String sqlName, String params, Supplier<List<T>> supplier) {
        long start = System.currentTimeMillis();
        //log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        List<T> result = supplier.get();
        long cost = System.currentTimeMillis() - start;
        //log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}, resultSize={}", sqlName, params, cost, result == null ? 0 : result.size());
        return result;
    }

    private void logSqlCommand(String sqlName, String params, Runnable runnable) {
        long start = System.currentTimeMillis();
        //log.info("员工跟进分析SQL开始, sqlName={}, params={}", sqlName, params);
        runnable.run();
        long cost = System.currentTimeMillis() - start;
        //log.info("员工跟进分析SQL结束, sqlName={}, params={}, costMs={}", sqlName, params, cost);
    }
}
