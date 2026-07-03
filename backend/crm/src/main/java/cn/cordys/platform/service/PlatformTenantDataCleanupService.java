package cn.cordys.platform.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.JSON;
import cn.cordys.config.TenantHikariDataSourceFactory;
import cn.cordys.platform.mapper.ExtTenantOpsTaskMapper;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.service.TenantMetaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlatformTenantDataCleanupService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int DELETE_BATCH_SIZE = 10000;
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private static final List<CleanupTable> CLEANUP_TABLES = List.of(
            CleanupTable.createTime("customer"),                    // 客户主表
            CleanupTable.createTime("clue"),                        // 线索主表
            CleanupTable.createTime("contract"),                    // 合同主表
            CleanupTable.eventTime("report_employee_stat_event"),   // 员工统计事件明细表
            CleanupTable.statDate("report_employee_stat_day"),      // 员工统计日汇总表
            CleanupTable.createTime("sys_operation_log"),           // 操作日志主表
            CleanupTable.createTime("sys_login_log"),               // 登录日志表
            CleanupTable.createTime("sys_notification"),            // 系统通知表
            CleanupTable.createTime("sys_message_task"),            // 消息任务表
            CleanupTable.createTime("sys_announcement"),            // 系统公告表
            CleanupTable.createTime("export_task"),                 // 导出任务表
            CleanupTable.createTime("contract_payment_plan"),       // 合同回款计划主表
            CleanupTable.createTime("contract_payment_record"),     // 合同回款记录主表
            CleanupTable.createTime("customer_contact"),            // 客户联系人主表
            CleanupTable.createTime("follow_up_record"),            // 跟进记录主表
            CleanupTable.createTime("follow_up_plan"),              // 跟进计划主表
            CleanupTable.createTime("business_title"),              // 工商抬头主表
            CleanupTable.createTime("contract_invoice"),            // 合同发票主表
            CleanupTable.createTime("sales_order")                  // 订单主表
    );

    private static final List<OrphanCleanupTable> ORPHAN_CLEANUP_TABLES = List.of(
            OrphanCleanupTable.single("customer_field", "resource_id", "customer"),                                  // 客户自定义字段表
            OrphanCleanupTable.single("customer_field_blob", "resource_id", "customer"),                             // 客户自定义字段大文本表
            OrphanCleanupTable.single("customer_contact_field", "resource_id", "customer_contact"),                  // 客户联系人自定义字段表
            OrphanCleanupTable.single("customer_contact_field_blob", "resource_id", "customer_contact"),             // 客户联系人自定义字段大文本表
            OrphanCleanupTable.single("customer_collaboration", "customer_id", "customer"),                          // 客户协作人表
            OrphanCleanupTable.single("customer_owner", "customer_id", "customer"),                                  // 客户历史负责人表
            OrphanCleanupTable.multi("customer_relation", List.of(                                                    // 客户关系表
                    OrphanParent.customer("source_customer_id"),
                    OrphanParent.customer("target_customer_id")
            )),
            OrphanCleanupTable.single("clue_field", "resource_id", "clue"),                                          // 线索自定义字段表
            OrphanCleanupTable.single("clue_field_blob", "resource_id", "clue"),                                     // 线索自定义字段大文本表
            OrphanCleanupTable.single("clue_owner", "clue_id", "clue"),                                              // 线索历史负责人表
            OrphanCleanupTable.single("follow_up_record_field", "resource_id", "follow_up_record"),                  // 跟进记录自定义字段表
            OrphanCleanupTable.single("follow_up_record_field_blob", "resource_id", "follow_up_record"),             // 跟进记录自定义字段大文本表
            OrphanCleanupTable.single("follow_up_plan_field", "resource_id", "follow_up_plan"),                      // 跟进计划自定义字段表
            OrphanCleanupTable.single("follow_up_plan_field_blob", "resource_id", "follow_up_plan"),                 // 跟进计划自定义字段大文本表
            OrphanCleanupTable.single("contract_field", "resource_id", "contract"),                                  // 合同自定义字段表
            OrphanCleanupTable.single("contract_field_blob", "resource_id", "contract"),                             // 合同自定义字段大文本表
            OrphanCleanupTable.single("contract_snapshot", "contract_id", "contract"),                               // 合同快照表
            OrphanCleanupTable.single("contract_invoice_field", "resource_id", "contract_invoice"),                  // 合同发票自定义字段表
            OrphanCleanupTable.single("contract_invoice_field_blob", "resource_id", "contract_invoice"),             // 合同发票自定义字段大文本表
            OrphanCleanupTable.single("contract_invoice_snapshot", "invoice_id", "contract_invoice"),                // 合同发票快照表
            OrphanCleanupTable.single("contract_payment_plan_field", "resource_id", "contract_payment_plan"),        // 合同回款计划自定义字段表
            OrphanCleanupTable.single("contract_payment_plan_field_blob", "resource_id", "contract_payment_plan"),   // 合同回款计划自定义字段大文本表
            OrphanCleanupTable.single("contract_payment_record_field", "resource_id", "contract_payment_record"),    // 合同回款记录自定义字段表
            OrphanCleanupTable.single("contract_payment_record_field_blob", "resource_id", "contract_payment_record"),// 合同回款记录自定义字段大文本表
            OrphanCleanupTable.single("sales_order_field", "resource_id", "sales_order"),                            // 订单自定义字段表
            OrphanCleanupTable.single("sales_order_field_blob", "resource_id", "sales_order"),                       // 订单自定义字段大文本表
            OrphanCleanupTable.single("sales_order_snapshot", "order_id", "sales_order"),                            // 订单快照表
            OrphanCleanupTable.single("sys_operation_log_blob", "id", "sys_operation_log")                           // 操作日志详情表
    );

    @Resource
    private ExtTenantOpsTaskMapper extTenantOpsTaskMapper;

    @Resource
    private TenantMetaService tenantMetaService;

    @Resource
    private TenantHikariDataSourceFactory tenantHikariDataSourceFactory;

    public void executeTask(String taskId, String tenantId, String operatorId, LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        List<String> successTables = new ArrayList<>();
        List<String> skippedTables = new ArrayList<>();
        Map<String, Long> tableDeletedRows = new LinkedHashMap<>();
        OrphanCleanupResult orphanCleanupResult = new OrphanCleanupResult();
        long startMillis = toStartMillis(startDate);
        long endExclusiveMillis = toStartMillis(endDate.plusDays(1));
        long[] totalDeletedRows = {0L};
        log.info("[租户数据清理-开始执行] 任务ID={}, 租户={}, 操作人={}, 开始日期={}, 结束日期={}, create_time开始毫秒={}, create_time结束毫秒不含={}, 批大小={}, 主数据计划清理表数={}, 冗余数据计划清理表数={}",
                taskId, tenantId, operatorId, startDate, endDate, startMillis, endExclusiveMillis, DELETE_BATCH_SIZE,
                CLEANUP_TABLES.size(), ORPHAN_CLEANUP_TABLES.size());
        updateTaskStatus(taskId, STATUS_RUNNING,
                detail("开始清理", null, startDate, endDate, successTables, skippedTables, totalDeletedRows[0], tableDeletedRows, null));
        DataSource dataSource = null;
        try {
            TenantDbConfigDTO config = tenantMetaService.getTenantDbConfig(tenantId);
            if (config == null) {
                log.warn("[租户数据清理-租户数据库配置缺失] 任务ID={}, 租户={}", taskId, tenantId);
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户不存在或数据库配置缺失");
            }
            log.info("[租户数据清理-租户数据库配置已加载] 任务ID={}, 租户={}, 数据库名={}, 是否启用={}",
                    taskId, tenantId, config.getDbName(), config.getEnabled());
            dataSource = tenantHikariDataSourceFactory.createTenantPool(
                    config.getDriverClassName(), config.getJdbcUrl(), config.getDbUsername(), config.getDbPassword(), tenantId);
            log.info("[租户数据清理-租户数据库连接池已创建] 任务ID={}, 租户={}, 数据库名={}",
                    taskId, tenantId, config.getDbName());
            deleteTenantTables(dataSource, taskId, tenantId, startDate, endDate, startMillis, endExclusiveMillis,
                    successTables, skippedTables, tableDeletedRows, totalDeletedRows);
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, null, null));
            cleanupOrphanTables(dataSource, taskId, tenantId, startDate, endDate, successTables, skippedTables,
                    tableDeletedRows, totalDeletedRows, orphanCleanupResult);
            String successMessage = orphanCleanupResult.failedTableErrors.isEmpty()
                    ? "清理成功，冗余数据清理完成"
                    : "清理成功，冗余数据清理完成，存在失败表";
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail(successMessage, null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, null, null));
            log.info("[租户数据清理-清理成功] 任务ID={}, 租户={}, 操作人={}, 主数据成功表数={}, 主数据跳过表数={}, 主数据累计删除行数={}, 主数据计划清理表数={}, 冗余数据成功表数={}, 冗余数据跳过表数={}, 冗余数据失败表数={}, 冗余数据累计删除行数={}, 冗余数据计划清理表数={}, 总耗时毫秒={}",
                    taskId, tenantId, operatorId, successTables.size(), skippedTables.size(), totalDeletedRows[0], CLEANUP_TABLES.size(),
                    orphanCleanupResult.successTables.size(), orphanCleanupResult.skippedTables.size(),
                    orphanCleanupResult.failedTableErrors.size(), orphanCleanupResult.deletedRows, ORPHAN_CLEANUP_TABLES.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            String errorMessage = safeError(ex);
            updateTaskStatus(taskId, STATUS_FAILED,
                    detail("清理失败", null, startDate, endDate, successTables, skippedTables, totalDeletedRows[0], tableDeletedRows, errorMessage));
            log.error("[租户数据清理-清理失败] 任务ID={}, 租户={}, 操作人={}, 成功表数={}, 跳过表数={}, 累计删除行数={}, 计划清理表数={}, 失败原因={}",
                    taskId, tenantId, operatorId, successTables.size(), skippedTables.size(), totalDeletedRows[0],
                    CLEANUP_TABLES.size(), errorMessage, ex);
        } finally {
            closeIfPossible(dataSource);
            log.info("[租户数据清理-租户数据库连接池已关闭] 任务ID={}, 租户={}", taskId, tenantId);
        }
    }

    private void cleanupOrphanTables(DataSource dataSource, String taskId, String tenantId,
                                     LocalDate startDate, LocalDate endDate,
                                     List<String> successTables, List<String> skippedTables,
                                     Map<String, Long> tableDeletedRows, long[] totalDeletedRows,
                                     OrphanCleanupResult orphanCleanupResult) {
        long start = System.currentTimeMillis();
        log.info("[租户数据清理-冗余数据清理开始] 任务ID={}, 租户={}, 计划清理表数={}",
                taskId, tenantId, ORPHAN_CLEANUP_TABLES.size());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            log.info("[租户数据清理-冗余数据数据库连接已打开] 任务ID={}, 租户={}, 当前数据库={}",
                    taskId, tenantId, connection.getCatalog());
            boolean foreignKeyChecksDisabled = false;
            try {
                log.info("[租户数据清理-冗余数据开始关闭外键检查] 任务ID={}, 租户={}", taskId, tenantId);
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                foreignKeyChecksDisabled = true;
                log.info("[租户数据清理-冗余数据外键检查已关闭] 任务ID={}, 租户={}", taskId, tenantId);
                for (OrphanCleanupTable table : ORPHAN_CLEANUP_TABLES) {
                    cleanupSingleOrphanTable(connection, taskId, tenantId, startDate, endDate,
                            successTables, skippedTables, tableDeletedRows, totalDeletedRows, orphanCleanupResult, table);
                }
            } finally {
                if (foreignKeyChecksDisabled) {
                    log.info("[租户数据清理-冗余数据开始恢复外键检查] 任务ID={}, 租户={}", taskId, tenantId);
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                    log.info("[租户数据清理-冗余数据外键检查已恢复] 任务ID={}, 租户={}", taskId, tenantId);
                }
            }
        } catch (Exception ex) {
            String errorMessage = safeError(ex);
            orphanCleanupResult.failedTableErrors.put("冗余数据清理", errorMessage);
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail("主数据清理完成，冗余数据清理失败", null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, null, null));
            log.error("[租户数据清理-冗余数据清理整体失败] 任务ID={}, 租户={}, 失败原因={}, 耗时毫秒={}",
                    taskId, tenantId, errorMessage, System.currentTimeMillis() - start, ex);
            return;
        }
        log.info("[租户数据清理-冗余数据清理结束] 任务ID={}, 租户={}, 成功表数={}, 跳过表数={}, 失败表数={}, 累计删除行数={}, 计划清理表数={}, 耗时毫秒={}",
                taskId, tenantId, orphanCleanupResult.successTables.size(), orphanCleanupResult.skippedTables.size(),
                orphanCleanupResult.failedTableErrors.size(), orphanCleanupResult.deletedRows,
                ORPHAN_CLEANUP_TABLES.size(), System.currentTimeMillis() - start);
    }

    private void cleanupSingleOrphanTable(Connection connection, String taskId, String tenantId,
                                          LocalDate startDate, LocalDate endDate,
                                          List<String> successTables, List<String> skippedTables,
                                          Map<String, Long> tableDeletedRows, long[] totalDeletedRows,
                                          OrphanCleanupResult orphanCleanupResult, OrphanCleanupTable table) {
        long tableStart = System.currentTimeMillis();
        try {
            validateOrphanTable(table);
            log.info("[租户数据清理-开始清理冗余表] 任务ID={}, 租户={}, 表名={}, 父表关系={}, 已成功表数={}, 已跳过表数={}, 已失败表数={}, 已删除冗余总行数={}, 计划清理冗余表数={}",
                    taskId, tenantId, table.name, table.parentDescription(), orphanCleanupResult.successTables.size(),
                    orphanCleanupResult.skippedTables.size(), orphanCleanupResult.failedTableErrors.size(),
                    orphanCleanupResult.deletedRows, ORPHAN_CLEANUP_TABLES.size());
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, table.name, null));
            if (!tableExists(connection, table.name)) {
                orphanCleanupResult.skippedTables.add(table.name);
                orphanCleanupResult.skippedTableReasons.put(table.name, "表不存在");
                log.info("[租户数据清理-跳过冗余表] 任务ID={}, 租户={}, 表名={}, 跳过原因={}, 已跳过表数={}, 耗时毫秒={}",
                        taskId, tenantId, table.name, "表不存在", orphanCleanupResult.skippedTables.size(),
                        System.currentTimeMillis() - tableStart);
                updateTaskStatus(taskId, STATUS_SUCCESS,
                        detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                                totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, table.name, null));
                return;
            }
            String missingParent = firstMissingParent(connection, table);
            if (missingParent != null) {
                orphanCleanupResult.skippedTables.add(table.name);
                orphanCleanupResult.skippedTableReasons.put(table.name, "父表不存在: " + missingParent);
                log.info("[租户数据清理-跳过冗余表] 任务ID={}, 租户={}, 表名={}, 跳过原因={}, 已跳过表数={}, 耗时毫秒={}",
                        taskId, tenantId, table.name, "父表不存在: " + missingParent,
                        orphanCleanupResult.skippedTables.size(), System.currentTimeMillis() - tableStart);
                updateTaskStatus(taskId, STATUS_SUCCESS,
                        detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                                totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, table.name, null));
                return;
            }
            long deletedRows = deleteOrphanTableInBatches(connection, taskId, tenantId, table);
            orphanCleanupResult.successTables.add(table.name);
            orphanCleanupResult.tableDeletedRows.put(table.name, deletedRows);
            orphanCleanupResult.deletedRows += deletedRows;
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, table.name, null));
            log.info("[租户数据清理-冗余表清理成功] 任务ID={}, 租户={}, 表名={}, 本表删除行数={}, 已删除冗余总行数={}, 已成功表数={}, 耗时毫秒={}",
                    taskId, tenantId, table.name, deletedRows, orphanCleanupResult.deletedRows,
                    orphanCleanupResult.successTables.size(), System.currentTimeMillis() - tableStart);
        } catch (Exception ex) {
            String errorMessage = safeError(ex);
            orphanCleanupResult.failedTableErrors.put(table.name, errorMessage);
            updateTaskStatus(taskId, STATUS_SUCCESS,
                    detail("主数据清理完成，冗余数据清理中", null, startDate, endDate, successTables, skippedTables,
                            totalDeletedRows[0], tableDeletedRows, orphanCleanupResult, table.name, null));
            log.error("[租户数据清理-冗余表清理失败] 任务ID={}, 租户={}, 表名={}, 失败原因={}, 耗时毫秒={}",
                    taskId, tenantId, table.name, errorMessage, System.currentTimeMillis() - tableStart, ex);
        }
    }

    private long deleteOrphanTableInBatches(Connection connection, String taskId, String tenantId,
                                            OrphanCleanupTable table) throws Exception {
        String sql = "DELETE FROM `" + table.name + "` WHERE " + table.deleteCondition() + " LIMIT " + DELETE_BATCH_SIZE;
        long tableDeleted = 0L;
        int batchNo = 0;
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            while (true) {
                batchNo++;
                long batchStart = System.currentTimeMillis();
                int affectedRows = preparedStatement.executeUpdate();
                tableDeleted += affectedRows;
                log.info("[租户数据清理-冗余表批次删除完成] 任务ID={}, 租户={}, 表名={}, 父表关系={}, 批次={}, 本批删除行数={}, 本表累计删除行数={}, 批大小={}, 耗时毫秒={}",
                        taskId, tenantId, table.name, table.parentDescription(), batchNo, affectedRows, tableDeleted,
                        DELETE_BATCH_SIZE, System.currentTimeMillis() - batchStart);
                if (affectedRows < DELETE_BATCH_SIZE) {
                    break;
                }
            }
        }
        return tableDeleted;
    }

    private void deleteTenantTables(DataSource dataSource, String taskId, String tenantId,
                                    LocalDate startDate, LocalDate endDate, long startMillis, long endExclusiveMillis,
                                    List<String> successTables, List<String> skippedTables,
                                    Map<String, Long> tableDeletedRows, long[] totalDeletedRows) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            log.info("[租户数据清理-数据库连接已打开] 任务ID={}, 租户={}, 当前数据库={}",
                    taskId, tenantId, connection.getCatalog());
            boolean foreignKeyChecksDisabled = false;
            try {
                log.info("[租户数据清理-开始关闭外键检查] 任务ID={}, 租户={}", taskId, tenantId);
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                foreignKeyChecksDisabled = true;
                log.info("[租户数据清理-外键检查已关闭] 任务ID={}, 租户={}", taskId, tenantId);
                for (CleanupTable table : CLEANUP_TABLES) {
                    long tableStart = System.currentTimeMillis();
                    validateTableName(table.name);
                    validateTableName(table.conditionColumn);
                    log.info("[租户数据清理-开始清理表] 任务ID={}, 租户={}, 表名={}, 条件字段={}, 开始日期={}, 结束日期={}, 已成功表数={}, 已跳过表数={}, 已删除总行数={}, 计划清理表数={}",
                            taskId, tenantId, table.name, table.conditionColumn, startDate, endDate,
                            successTables.size(), skippedTables.size(), totalDeletedRows[0], CLEANUP_TABLES.size());
                    updateTaskStatus(taskId, STATUS_RUNNING,
                            detail("清理中", table.name, startDate, endDate, successTables, skippedTables,
                                    totalDeletedRows[0], tableDeletedRows, null));
                    if (!tableExists(connection, table.name)) {
                        skippedTables.add(table.name);
                        log.info("[租户数据清理-跳过表] 任务ID={}, 租户={}, 表名={}, 跳过原因={}, 已跳过表数={}, 耗时毫秒={}",
                                taskId, tenantId, table.name, "表不存在", skippedTables.size(), System.currentTimeMillis() - tableStart);
                        continue;
                    }
                    long tableDeleted = 0L;
                    try {
                        tableDeleted = deleteTableInBatches(connection, taskId, tenantId, table, startDate, endDate,
                                startMillis, endExclusiveMillis);
                    } catch (Exception ex) {
                        log.error("[租户数据清理-清理表失败] 任务ID={}, 租户={}, 表名={}, 耗时毫秒={}",
                                taskId, tenantId, table.name, System.currentTimeMillis() - tableStart, ex);
                        throw new IllegalStateException("清理表失败: " + table.name, ex);
                    }
                    totalDeletedRows[0] += tableDeleted;
                    tableDeletedRows.put(table.name, tableDeleted);
                    successTables.add(table.name);
                    updateTaskStatus(taskId, STATUS_RUNNING,
                            detail("清理中", table.name, startDate, endDate, successTables, skippedTables,
                                    totalDeletedRows[0], tableDeletedRows, null));
                    log.info("[租户数据清理-清理表成功] 任务ID={}, 租户={}, 表名={}, 本表删除行数={}, 已删除总行数={}, 已成功表数={}, 耗时毫秒={}",
                            taskId, tenantId, table.name, tableDeleted, totalDeletedRows[0], successTables.size(),
                            System.currentTimeMillis() - tableStart);
                }
            } finally {
                if (foreignKeyChecksDisabled) {
                    log.info("[租户数据清理-开始恢复外键检查] 任务ID={}, 租户={}", taskId, tenantId);
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                    log.info("[租户数据清理-外键检查已恢复] 任务ID={}, 租户={}", taskId, tenantId);
                }
            }
        }
    }

    private long deleteTableInBatches(Connection connection, String taskId, String tenantId, CleanupTable table,
                                      LocalDate startDate, LocalDate endDate, long startMillis,
                                      long endExclusiveMillis) throws Exception {
        String sql = "DELETE FROM `" + table.name + "` WHERE `" + table.conditionColumn + "` >= ? AND `"
                + table.conditionColumn + "` " + (table.statDate ? "<= ?" : "< ?") + " LIMIT " + DELETE_BATCH_SIZE;
        long tableDeleted = 0L;
        int batchNo = 0;
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            while (true) {
                batchNo++;
                long batchStart = System.currentTimeMillis();
                if (table.statDate) {
                    preparedStatement.setDate(1, Date.valueOf(startDate));
                    preparedStatement.setDate(2, Date.valueOf(endDate));
                } else {
                    preparedStatement.setLong(1, startMillis);
                    preparedStatement.setLong(2, endExclusiveMillis);
                }
                int affectedRows = preparedStatement.executeUpdate();
                tableDeleted += affectedRows;
                log.info("[租户数据清理-批次删除完成] 任务ID={}, 租户={}, 表名={}, 条件字段={}, 批次={}, 本批删除行数={}, 本表累计删除行数={}, 批大小={}, 耗时毫秒={}",
                        taskId, tenantId, table.name, table.conditionColumn, batchNo, affectedRows, tableDeleted,
                        DELETE_BATCH_SIZE, System.currentTimeMillis() - batchStart);
                if (affectedRows < DELETE_BATCH_SIZE) {
                    break;
                }
            }
        }
        return tableDeleted;
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String firstMissingParent(Connection connection, OrphanCleanupTable table) throws Exception {
        for (OrphanParent parent : table.parents) {
            if (!tableExists(connection, parent.parentTable)) {
                return parent.parentTable;
            }
        }
        return null;
    }

    private void updateTaskStatus(String taskId, String status, String detail) {
        extTenantOpsTaskMapper.updateTaskStatus(taskId, status, StringUtils.left(detail, 2000), System.currentTimeMillis());
    }

    private String detail(String message, String currentTable, LocalDate startDate, LocalDate endDate,
                          List<String> successTables, List<String> skippedTables, long deletedRows,
                          Map<String, Long> tableDeletedRows, String errorMessage) {
        return detail(message, currentTable, startDate, endDate, successTables, skippedTables, deletedRows,
                tableDeletedRows, null, null, errorMessage);
    }

    private String detail(String message, String currentTable, LocalDate startDate, LocalDate endDate,
                          List<String> successTables, List<String> skippedTables, long deletedRows,
                          Map<String, Long> tableDeletedRows, OrphanCleanupResult orphanCleanupResult,
                          String orphanCurrentTable, String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", message);
        detail.put("startDate", startDate == null ? null : startDate.toString());
        detail.put("endDate", endDate == null ? null : endDate.toString());
        detail.put("currentTable", currentTable);
        detail.put("successCount", successTables.size());
        detail.put("skippedCount", skippedTables.size());
        detail.put("skippedTables", skippedTables);
        detail.put("skippedTableReasons", skippedTables.stream()
                .collect(Collectors.toMap(Function.identity(), table -> "表不存在", (first, second) -> first, LinkedHashMap::new)));
        detail.put("totalTables", CLEANUP_TABLES.size());
        detail.put("deletedRows", deletedRows);
        detail.put("tableDeletedRows", tableDeletedRows);
        if (orphanCleanupResult != null) {
            detail.put("redundantCurrentTable", orphanCurrentTable);
            detail.put("redundantTotalTables", ORPHAN_CLEANUP_TABLES.size());
            detail.put("redundantSuccessCount", orphanCleanupResult.successTables.size());
            detail.put("redundantSkippedCount", orphanCleanupResult.skippedTables.size());
            detail.put("redundantFailedCount", orphanCleanupResult.failedTableErrors.size());
            detail.put("redundantDeletedRows", orphanCleanupResult.deletedRows);
            detail.put("redundantSkippedTables", orphanCleanupResult.skippedTables);
            detail.put("redundantSkippedTableReasons", orphanCleanupResult.skippedTableReasons);
            detail.put("redundantFailedTableErrors", orphanCleanupResult.failedTableErrors);
        }
        detail.put("error", errorMessage);
        return JSON.toJSONString(detail);
    }

    private long toStartMillis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void validateTableName(String table) {
        if (!TABLE_NAME_PATTERN.matcher(table).matches()) {
            throw new IllegalStateException("非法清理表名: " + table);
        }
    }

    private void validateOrphanTable(OrphanCleanupTable table) {
        validateTableName(table.name);
        for (OrphanParent parent : table.parents) {
            validateTableName(parent.childColumn);
            validateTableName(parent.parentTable);
            validateTableName(parent.parentColumn);
        }
    }

    private String safeError(Exception e) {
        if (e == null) {
            return "unknown";
        }
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null && StringUtils.isNotBlank(cause.getMessage())) {
            message = StringUtils.defaultString(message) + " | " + cause.getMessage();
        }
        return StringUtils.left(StringUtils.defaultIfBlank(message, e.getClass().getSimpleName()), 1000);
    }

    private void closeIfPossible(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
            }
        }
    }

    private static class CleanupTable {
        private final String name;
        private final String conditionColumn;
        private final boolean statDate;

        private CleanupTable(String name, String conditionColumn, boolean statDate) {
            this.name = name;
            this.conditionColumn = conditionColumn;
            this.statDate = statDate;
        }

        private static CleanupTable createTime(String name) {
            return new CleanupTable(name, "create_time", false);
        }

        private static CleanupTable eventTime(String name) {
            return new CleanupTable(name, "event_time", false);
        }

        private static CleanupTable statDate(String name) {
            return new CleanupTable(name, "stat_date", true);
        }
    }

    private static class OrphanCleanupResult {
        private final List<String> successTables = new ArrayList<>();
        private final List<String> skippedTables = new ArrayList<>();
        private final Map<String, String> skippedTableReasons = new LinkedHashMap<>();
        private final Map<String, String> failedTableErrors = new LinkedHashMap<>();
        private final Map<String, Long> tableDeletedRows = new LinkedHashMap<>();
        private long deletedRows;
    }

    private static class OrphanCleanupTable {
        private final String name;
        private final List<OrphanParent> parents;

        private OrphanCleanupTable(String name, List<OrphanParent> parents) {
            this.name = name;
            this.parents = parents;
        }

        private static OrphanCleanupTable single(String name, String childColumn, String parentTable) {
            return new OrphanCleanupTable(name, List.of(new OrphanParent(childColumn, parentTable, "id")));
        }

        private static OrphanCleanupTable multi(String name, List<OrphanParent> parents) {
            return new OrphanCleanupTable(name, parents);
        }

        private String deleteCondition() {
            return parents.stream()
                    .map(parent -> "NOT EXISTS (SELECT 1 FROM `" + parent.parentTable + "` WHERE `"
                            + parent.parentTable + "`.`" + parent.parentColumn + "` = `" + name + "`.`"
                            + parent.childColumn + "`)")
                    .collect(Collectors.joining(" OR "));
        }

        private String parentDescription() {
            return parents.stream()
                    .map(parent -> parent.childColumn + "->" + parent.parentTable + "." + parent.parentColumn)
                    .collect(Collectors.joining(","));
        }
    }

    private static class OrphanParent {
        private final String childColumn;
        private final String parentTable;
        private final String parentColumn;

        private OrphanParent(String childColumn, String parentTable, String parentColumn) {
            this.childColumn = childColumn;
            this.parentTable = parentTable;
            this.parentColumn = parentColumn;
        }

        private static OrphanParent customer(String childColumn) {
            return new OrphanParent(childColumn, "customer", "id");
        }
    }
}
