package cn.cordys.platform.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.schedule.TenantQuartzLifecycleService;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.config.DynamicTenantRoutingDataSource;
import cn.cordys.config.TenantHikariDataSourceFactory;
import cn.cordys.platform.domain.TenantOpsTask;
import cn.cordys.platform.dto.request.PlatformAuditPageRequest;
import cn.cordys.platform.dto.request.PlatformTenantDataCleanupTaskPageRequest;
import cn.cordys.platform.dto.request.PlatformTenantPageRequest;
import cn.cordys.platform.dto.response.PlatformAuditLogResponse;
import cn.cordys.platform.dto.response.PlatformTenantDataCleanupSubmitResponse;
import cn.cordys.platform.dto.response.PlatformTenantDataCleanupTaskResponse;
import cn.cordys.platform.dto.response.PlatformTenantHealthResponse;
import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import cn.cordys.platform.dto.response.PlatformTenantProvisionTaskResponse;
import cn.cordys.platform.mapper.ExtTenantOpsTaskMapper;
import cn.cordys.tenant.dto.response.TenantProvisionResponse;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import cn.cordys.tenant.service.TenantJdbcResolver;
import cn.cordys.tenant.service.TenantMetaService;
import cn.cordys.tenant.service.TenantProvisioningService;
import cn.cordys.tenant.dto.TenantShadowMetaDTO;
import cn.cordys.tenant.service.TenantShadowProvisioningService;
import cn.cordys.tenant.service.TenantShadowSwitchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class PlatformAdminService {
    
    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private TenantJdbcResolver tenantJdbcResolver;

    @Resource
    private ExtTenantOpsTaskMapper extTenantOpsTaskMapper;

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    @Resource
    private TenantMetaService tenantMetaService;

    @Resource
    private DynamicTenantRoutingDataSource tenantRoutingDataSource;

    @Resource
    private TenantHikariDataSourceFactory tenantHikariDataSourceFactory;

    @Resource
    private TenantProvisioningService tenantProvisioningService;

    @Resource
    private ObjectProvider<TenantQuartzLifecycleService> tenantQuartzLifecycleServiceProvider;

    @Resource
    private PlatformTenantDataCleanupTaskExecutor platformTenantDataCleanupTaskExecutor;

    @Resource
    private TenantShadowProvisioningService tenantShadowProvisioningService;

    @Resource
    private TenantShadowSwitchService tenantShadowSwitchService;

    public Pager<List<PlatformTenantItemResponse>> pageTenants(PlatformTenantPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize());
        int offset = (current - 1) * pageSize;
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String like = "%" + keyword + "%";

        Long total = extTenantMapper.countByKeyword(keyword, like);
        List<PlatformTenantItemResponse> list = extTenantMapper.pageByKeyword(keyword, like, pageSize, offset);
        if (list != null) {
            for (PlatformTenantItemResponse item : list) {
                tenantJdbcResolver.enrichPlatformTenantItem(item);
            }
        }
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public PlatformTenantItemResponse getTenantDetail(String tenantId) {
        PlatformTenantItemResponse detail = extTenantMapper.selectDetailByTenantId(tenantId);
        if (detail == null) {
            throw new GenericException("租户不存在");
        }
        tenantJdbcResolver.enrichPlatformTenantItem(detail);
        return detail;
    }

    public void updateTenantStatus(String tenantId, boolean enabled, String operatorId) {
        long now = System.currentTimeMillis();
        String status = enabled ? "ACTIVE" : "FROZEN";
        int tenantUpdated = extTenantMapper.updateTenantStatus(tenantId, status, now, operatorId);
        if (tenantUpdated <= 0) {
            throw new GenericException("租户不存在");
        }
        tenantMetaService.evictEnabledTenantOrgMapCache();
        if (!enabled) {
            purgeTenantQuartzSchedules(tenantId);
            tenantRoutingDataSource.unregisterTenantDataSource(tenantId);
            log.info("[TENANT_FREEZE] tenantId={}, operator={}", tenantId, operatorId);
        } else {
            TenantDbConfigDTO cfg = tenantMetaService.getTenantDbConfig(tenantId);
            if (cfg != null && !tenantRoutingDataSource.hasTenantDataSource(tenantId)) {
                tenantRoutingDataSource.registerTenantDataSource(tenantId,
                        Objects.requireNonNull(tenantHikariDataSourceFactory.createTenantPool(
                                cfg.getDriverClassName(), cfg.getJdbcUrl(),
                                cfg.getDbUsername(), cfg.getDbPassword(), tenantId)));
            }
            initializeTenantQuartzSchedules(tenantId);
            log.info("[TENANT_UNFREEZE] tenantId={}, operator={}", tenantId, operatorId);
        }
        recordAudit(operatorId, enabled ? "TENANT_UNFREEZE" : "TENANT_FREEZE", tenantId, "SUCCESS", "", 0L);
    }

    public PlatformTenantProvisionTaskResponse submitTenantProvisionTask(String tenantCode, String tenantName, String operatorId,
                                                                         List<String> initialUserIds, String orgId) {
        String normalizedTenantId = StringUtils.trimToEmpty(tenantCode).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalizedTenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户编码不能为空");
        }
        PlatformTenantProvisionTaskResponse latest = findLatestRunningProvisionTask(normalizedTenantId);
        if (latest != null) {
            log.info("[TENANT_PROVISION_DEDUP] tenantId={}, taskId={}",
                    normalizedTenantId, latest.getTaskId());
            return latest;
        }

        long now = System.currentTimeMillis();
        String taskId = IDGenerator.nextStr();
        String detail = "name=" + StringUtils.defaultString(tenantName);
        TenantOpsTask task = new TenantOpsTask();
        task.setId(taskId);
        task.setTenantId(normalizedTenantId);
        task.setTaskType("TENANT_PROVISION");
        task.setStatus("PENDING");
        task.setDetail(detail);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setOperatorId(operatorId);
        extTenantOpsTaskMapper.insertTask(task);
        log.info("[TENANT_PROVISION_SUBMITTED] taskId={}, tenantId={}, operator={}",
                taskId, normalizedTenantId, operatorId);

        return getTenantProvisionTask(taskId);
    }

    public PlatformTenantProvisionTaskResponse getTenantProvisionTask(String taskId) {
        PlatformTenantProvisionTaskResponse task = extTenantOpsTaskMapper.selectProvisionTaskById(taskId);
        if (task == null) {
            throw new GenericException("任务不存在");
        }
        return task;
    }

    public PlatformTenantDataCleanupSubmitResponse submitTenantDataCleanupTasks(List<String> tenantIds, String startDateText,
                                                                                String endDateText, String operatorId) {
        Set<String> normalizedTenantIds = normalizeTenantIds(tenantIds);
        LocalDate startDate = parseCleanupDate(startDateText, "开始日期");
        LocalDate endDate = parseCleanupDate(endDateText, "结束日期");
        if (startDate.isAfter(endDate)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "开始日期不能大于结束日期");
        }
        log.info("[租户数据清理-整理租户参数] 操作人={}, 原始租户参数={}, 去重后租户={}, 开始日期={}, 结束日期={}",
                operatorId, tenantIds, normalizedTenantIds, startDate, endDate);
        if (normalizedTenantIds.isEmpty()) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "请选择租户");
        }
        for (String tenantId : normalizedTenantIds) {
            if (!tenantMetaService.existsTenantId(tenantId)) {
                log.warn("[租户数据清理-租户不存在] 操作人={}, 租户={}", operatorId, tenantId);
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户不存在: " + tenantId);
            }
            log.info("[租户数据清理-租户校验通过] 操作人={}, 租户={}", operatorId, tenantId);
        }
        List<PlatformTenantDataCleanupTaskResponse> tasks = new ArrayList<>();
        for (String tenantId : normalizedTenantIds) {
            PlatformTenantDataCleanupTaskResponse latest = extTenantOpsTaskMapper.selectLatestRunningDataCleanupTask(tenantId);
            if (latest != null) {
                log.info("[租户数据清理-已有执行中任务] 操作人={}, 租户={}, 已有任务ID={}, 当前状态={}",
                        operatorId, tenantId, latest.getTaskId(), latest.getStatus());
                tasks.add(latest);
                continue;
            }
            long now = System.currentTimeMillis();
            String taskId = IDGenerator.nextStr();
            TenantOpsTask task = new TenantOpsTask();
            task.setId(taskId);
            task.setTenantId(tenantId);
            task.setTaskType("TENANT_DATA_CLEANUP");
            task.setStatus("PENDING");
            task.setDetail(buildCleanupPendingDetail(startDate, endDate));
            task.setCreateTime(now);
            task.setUpdateTime(now);
            task.setOperatorId(operatorId);
            extTenantOpsTaskMapper.insertTask(task);
            log.info("[租户数据清理-任务已创建] 操作人={}, 租户={}, 任务ID={}, 开始日期={}, 结束日期={}",
                    operatorId, tenantId, taskId, startDate, endDate);
            recordAudit(operatorId, "TENANT_DATA_CLEANUP_SUBMIT", tenantId, "SUCCESS",
                    "taskId=" + taskId + ",startDate=" + startDate + ",endDate=" + endDate, 0L);
            platformTenantDataCleanupTaskExecutor.executeCleanupTask(taskId, tenantId, operatorId, startDate, endDate);
            log.info("[租户数据清理-异步任务已派发] 操作人={}, 租户={}, 任务ID={}, 开始日期={}, 结束日期={}",
                    operatorId, tenantId, taskId, startDate, endDate);
            tasks.add(getTenantDataCleanupTask(taskId));
        }
        PlatformTenantDataCleanupSubmitResponse response = new PlatformTenantDataCleanupSubmitResponse();
        response.setTasks(tasks);
        log.info("[租户数据清理-提交处理完成] 操作人={}, 请求租户数={}, 返回任务数={}",
                operatorId, normalizedTenantIds.size(), tasks.size());
        return response;
    }

    private LocalDate parseCleanupDate(String dateText, String fieldName) {
        String normalized = StringUtils.trimToEmpty(dateText);
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, fieldName + "格式必须为yyyy-MM-dd");
        }
    }

    private String buildCleanupPendingDetail(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", "等待清理");
        detail.put("startDate", startDate.toString());
        detail.put("endDate", endDate.toString());
        detail.put("successCount", 0);
        detail.put("skippedCount", 0);
        detail.put("totalTables", 19);
        detail.put("deletedRows", 0);
        detail.put("redundantTotalTables", 28);
        detail.put("redundantSuccessCount", 0);
        detail.put("redundantSkippedCount", 0);
        detail.put("redundantFailedCount", 0);
        detail.put("redundantDeletedRows", 0);
        return JSON.toJSONString(detail);
    }

    public PlatformTenantDataCleanupTaskResponse getTenantDataCleanupTask(String taskId) {
        PlatformTenantDataCleanupTaskResponse task = extTenantOpsTaskMapper.selectDataCleanupTaskById(taskId);
        if (task == null) {
            throw new GenericException("任务不存在");
        }
        return task;
    }

    public Pager<List<PlatformTenantDataCleanupTaskResponse>> pageTenantDataCleanupTasks(PlatformTenantDataCleanupTaskPageRequest request) {
        int current = Math.max(1, request.getCurrent() == null ? 1 : request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize() == null ? 20 : request.getPageSize());
        int offset = (current - 1) * pageSize;
        String tenantId = StringUtils.trimToEmpty(request.getTenantId());
        String status = StringUtils.trimToEmpty(request.getStatus());
        Long total = extTenantOpsTaskMapper.countDataCleanupTasks(tenantId, status);
        List<PlatformTenantDataCleanupTaskResponse> list =
                extTenantOpsTaskMapper.pageDataCleanupTasks(tenantId, status, pageSize, offset);
        log.info("[租户数据清理-任务分页完成] 租户={}, 状态={}, 当前页={}, 每页数量={}, 总数={}, 本页数量={}",
                tenantId, status, current, pageSize, total, list == null ? 0 : list.size());
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public void executeProvisionTaskInternal(String taskId, String tenantCode, String tenantName, String operatorId,
                                             List<String> initialUserIds, String orgId) {
        long start = System.currentTimeMillis();
        updateTaskStatus(taskId, "RUNNING", "provision running");
        log.info("[TENANT_PROVISION_START] taskId={}, tenantId={}, operator={}",
                taskId, tenantCode, operatorId);
        try {
            TenantProvisionResponse response = tenantProvisioningService.provision(tenantCode, tenantName, operatorId, initialUserIds, orgId);
            String detail = "tenantId=" + response.getTenantId() + ",dbName=" + response.getDbName();
            updateTaskStatus(taskId, "SUCCESS", detail);
            recordAudit(operatorId, "TENANT_PROVISION", response.getTenantId(), "SUCCESS", detail,
                    System.currentTimeMillis() - start);
            log.info("[TENANT_PROVISION_SUCCESS] taskId={}, tenantId={}",
                    taskId, response.getTenantId());
        } catch (Exception e) {
            String detail = safeError(e);
            updateTaskStatus(taskId, "FAILED", detail);
            recordAudit(operatorId, "TENANT_PROVISION", tenantCode, "FAILED", detail,
                    System.currentTimeMillis() - start);
            log.error("[TENANT_PROVISION_FAILED] taskId={}, tenantId={}, error={}",
                    taskId, tenantCode, detail, e);
        }
    }

    private PlatformTenantProvisionTaskResponse findLatestRunningProvisionTask(String tenantId) {
        return extTenantOpsTaskMapper.selectLatestRunningProvisionTask(tenantId);
    }

    private Set<String> normalizeTenantIds(List<String> tenantIds) {
        Set<String> result = new LinkedHashSet<>();
        if (tenantIds == null) {
            return result;
        }
        for (String tenantId : tenantIds) {
            String normalized = StringUtils.trimToNull(tenantId);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private void updateTaskStatus(String taskId, String status, String detail) {
        long now = System.currentTimeMillis();
        extTenantOpsTaskMapper.updateTaskStatus(taskId, status, StringUtils.left(StringUtils.defaultString(detail), 2000), now);
    }

    private String safeError(Exception e) {
        if (e == null) {
            return "unknown";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(e.getMessage())) {
            parts.add(e.getMessage());
        }
        Throwable cause = e.getCause();
        if (cause != null && StringUtils.isNotBlank(cause.getMessage())) {
            parts.add(cause.getMessage());
        }
        String combined = String.join(" | ", parts);
        return StringUtils.left(StringUtils.defaultIfBlank(combined, e.getClass().getSimpleName()), 1800);
    }

    public PlatformTenantHealthResponse checkTenantHealth(String tenantId) {
        TenantDbConfigDTO config = tenantMetaService.getTenantDbConfig(tenantId);
        boolean metadataExists = tenantMetaService.existsTenantId(tenantId);
        boolean datasourceRegistered = tenantRoutingDataSource.hasTenantDataSource(tenantId);
        boolean jdbcReachable = false;
        String migrationVersion = "";
        if (metadataExists) {
            try {
                DataSource ds = tenantHikariDataSourceFactory.createTenantPool(
                        config.getDriverClassName(), config.getJdbcUrl(),
                        config.getDbUsername(), config.getDbPassword(), tenantId);
                JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                jdbcReachable = true;
                String sql = "SELECT version FROM " + "cordys_crm_version ORDER BY installed_rank DESC LIMIT 1";
                List<String> versions = jdbcTemplate.queryForList(sql, String.class);
                if (!versions.isEmpty()) {
                    migrationVersion = versions.get(0);
                }
                closeIfPossible(ds);
            } catch (Exception ignored) {
                jdbcReachable = false;
            }
        }
        return PlatformTenantHealthResponse.builder()
                .tenantId(tenantId)
                .metadataExists(metadataExists)
                .datasourceRegistered(datasourceRegistered)
                .jdbcReachable(jdbcReachable)
                .migrationVersion(migrationVersion)
                .build();
    }

    public void rerunTenantMigrate(String tenantId, String operatorId) {
        long start = System.currentTimeMillis();
        TenantDbConfigDTO config = tenantMetaService.getTenantDbConfig(tenantId);
        if (config == null) {
            throw new GenericException("租户不存在");
        }
        DataSource ds = tenantHikariDataSourceFactory.createTenantPool(
                config.getDriverClassName(), config.getJdbcUrl(),
                config.getDbUsername(), config.getDbPassword(), tenantId);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:migration")
                .encoding(StandardCharsets.UTF_8)
                .table("cordys_crm_version")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(false)
                .load();
        flyway.migrate();
        closeIfPossible(ds);
        long now = System.currentTimeMillis();
        TenantOpsTask task = new TenantOpsTask();
        task.setId(IDGenerator.nextStr());
        task.setTenantId(tenantId);
        task.setTaskType("RERUN_MIGRATE");
        task.setStatus("SUCCESS");
        task.setDetail("migrated");
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setOperatorId(operatorId);
        extTenantOpsTaskMapper.insertTask(task);
        recordAudit(operatorId, "TENANT_RERUN_MIGRATE", tenantId, "SUCCESS", "", now - start);
    }

    public void updateTenantOrgId(String tenantId, String orgId, String operatorId) {
        String normalizedOrgId = StringUtils.trimToNull(orgId);
        if (normalizedOrgId == null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "orgId 参数不能为空");
        }
        int updated = extTenantMapper.updateTenantOrgId(tenantId, normalizedOrgId, System.currentTimeMillis(), operatorId);
        if (updated <= 0) {
            throw new GenericException("租户不存在");
        }
        tenantMetaService.evictEnabledTenantOrgMapCache();
        recordAudit(operatorId, "TENANT_ORG_ID_UPDATE", tenantId, "SUCCESS", "orgId=" + normalizedOrgId, 0L);
    }

    public void updateTenantName(String tenantId, String name, String operatorId) {
        String normalizedName = StringUtils.trimToNull(name);
        if (normalizedName == null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "name 参数不能为空");
        }
        int updated = extTenantMapper.updateTenantName(tenantId, normalizedName, System.currentTimeMillis(), operatorId);
        if (updated <= 0) {
            throw new GenericException("租户不存在");
        }
        recordAudit(operatorId, "TENANT_NAME_UPDATE", tenantId, "SUCCESS", "name=" + normalizedName, 0L);
    }

    public Pager<List<PlatformAuditLogResponse>> pageAuditLogs(PlatformAuditPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize());
        int offset = (current - 1) * pageSize;
        String tenantId = StringUtils.trimToEmpty(request.getTenantId());
        String countSql = "SELECT COUNT(1) FROM platform_audit_log WHERE (? = '' OR tenant_id = ?)";
        Long total = masterJdbcTemplate.queryForObject(countSql, Long.class, tenantId, tenantId);
        String sql = "SELECT id, operator_id, action, tenant_id, result, detail, duration_ms, create_time " +
                "FROM platform_audit_log WHERE (? = '' OR tenant_id = ?) ORDER BY create_time DESC LIMIT ? OFFSET ?";
        List<PlatformAuditLogResponse> list = masterJdbcTemplate.query(sql, (rs, rowNum) -> {
            PlatformAuditLogResponse item = new PlatformAuditLogResponse();
            item.setId(rs.getString("id"));
            item.setOperatorId(rs.getString("operator_id"));
            item.setAction(rs.getString("action"));
            item.setTenantId(rs.getString("tenant_id"));
            item.setResult(rs.getString("result"));
            item.setDetail(rs.getString("detail"));
            item.setDurationMs(rs.getLong("duration_ms"));
            item.setCreateTime(rs.getLong("create_time"));
            return item;
        }, tenantId, tenantId, pageSize, offset);
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public void recordAudit(String operatorId, String action, String tenantId, String result, String detail, long durationMs) {
        masterJdbcTemplate.update("INSERT INTO platform_audit_log (id, operator_id, action, tenant_id, result, detail, duration_ms, create_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                IDGenerator.nextStr(), operatorId, action, tenantId, result, detail, durationMs, System.currentTimeMillis());
    }

    private void closeIfPossible(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
            }
        }
    }

    private void initializeTenantQuartzSchedules(String tenantId) {
        TenantQuartzLifecycleService lifecycle = tenantQuartzLifecycleServiceProvider.getIfAvailable();
        if (lifecycle != null) {
            lifecycle.initializeTenantSchedules(tenantId);
        }
    }

    private void purgeTenantQuartzSchedules(String tenantId) {
        TenantQuartzLifecycleService lifecycle = tenantQuartzLifecycleServiceProvider.getIfAvailable();
        if (lifecycle != null) {
            lifecycle.purgeTenantSchedules(tenantId);
        }
    }

    public void enableTenantShadow(String tenantId, String operatorId) {
        tenantShadowProvisioningService.enableShadow(tenantId, operatorId);
        recordAudit(operatorId, "TENANT_SHADOW_ENABLE", tenantId, "SUCCESS", "", 0L);
    }

    public TenantShadowMetaDTO getTenantShadowStatus(String tenantId) {
        TenantShadowMetaDTO meta = tenantShadowSwitchService.getShadowStatus(tenantId);
        if (meta == null) {
            throw new GenericException("租户不存在");
        }
        return meta;
    }

    public void switchTenantToShadow(String tenantId, String operatorId) {
        tenantShadowSwitchService.switchToShadowAsync(tenantId, operatorId);
        recordAudit(operatorId, "TENANT_SWITCH_TO_SHADOW", tenantId, "ACCEPTED", "async", 0L);
    }

    public void switchTenantToPrimary(String tenantId, String operatorId) {
        tenantShadowSwitchService.switchToPrimary(tenantId, operatorId);
        recordAudit(operatorId, "TENANT_SWITCH_TO_PRIMARY", tenantId, "SUCCESS", "", 0L);
    }
}
