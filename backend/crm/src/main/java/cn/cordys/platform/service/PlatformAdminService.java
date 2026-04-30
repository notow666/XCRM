package cn.cordys.platform.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.config.DynamicTenantRoutingDataSource;
import cn.cordys.platform.domain.TenantOpsTask;
import cn.cordys.platform.dto.request.PlatformAuditPageRequest;
import cn.cordys.platform.dto.request.PlatformTenantPageRequest;
import cn.cordys.platform.dto.response.PlatformAuditLogResponse;
import cn.cordys.platform.dto.response.PlatformTenantHealthResponse;
import cn.cordys.platform.dto.response.PlatformTenantItemResponse;
import cn.cordys.platform.dto.response.PlatformTenantProvisionTaskResponse;
import cn.cordys.platform.mapper.ExtTenantOpsTaskMapper;
import cn.cordys.tenant.dto.response.TenantProvisionResponse;
import cn.cordys.tenant.dto.TenantDbConfigDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import cn.cordys.tenant.service.TenantJdbcResolver;
import cn.cordys.tenant.service.TenantProvisioningService;
import cn.cordys.tenant.service.TenantMetaService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PlatformAdminService {

    private static final Logger managementLog = LoggerFactory.getLogger("MANAGEMENT_CENTER_LOG");

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
    private TenantProvisioningService tenantProvisioningService;

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
        if (!enabled) {
            tenantRoutingDataSource.unregisterTenantDataSource(tenantId);
            managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_FREEZE] tenantId={}, operator={}", tenantId, operatorId);
        } else {
            TenantDbConfigDTO cfg = tenantMetaService.getTenantDbConfig(tenantId);
            if (cfg != null && !tenantRoutingDataSource.hasTenantDataSource(tenantId)) {
                tenantRoutingDataSource.registerTenantDataSource(tenantId, Objects.requireNonNull(createDataSource(cfg)));
            }
            managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_UNFREEZE] tenantId={}, operator={}", tenantId, operatorId);
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
            managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_DEDUP] tenantId={}, taskId={}",
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
        managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_SUBMITTED] taskId={}, tenantId={}, operator={}",
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

    public void executeProvisionTaskInternal(String taskId, String tenantCode, String tenantName, String operatorId,
                                             List<String> initialUserIds, String orgId) {
        long start = System.currentTimeMillis();
        updateTaskStatus(taskId, "RUNNING", "provision running");
        managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_START] taskId={}, tenantId={}, operator={}",
                taskId, tenantCode, operatorId);
        try {
            TenantProvisionResponse response = tenantProvisioningService.provision(tenantCode, tenantName, operatorId, initialUserIds, orgId);
            String detail = "tenantId=" + response.getTenantId() + ",dbName=" + response.getDbName();
            updateTaskStatus(taskId, "SUCCESS", detail);
            recordAudit(operatorId, "TENANT_PROVISION", response.getTenantId(), "SUCCESS", detail,
                    System.currentTimeMillis() - start);
            managementLog.info(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_SUCCESS] taskId={}, tenantId={}",
                    taskId, response.getTenantId());
        } catch (Exception e) {
            String detail = safeError(e);
            updateTaskStatus(taskId, "FAILED", detail);
            recordAudit(operatorId, "TENANT_PROVISION", tenantCode, "FAILED", detail,
                    System.currentTimeMillis() - start);
            managementLog.error(LogModule.MANAGEMENT_MARKER,"[MANAGEMENT_CENTER][TENANT_PROVISION_FAILED] taskId={}, tenantId={}, error={}",
                    taskId, tenantCode, detail, e);
        }
    }

    private PlatformTenantProvisionTaskResponse findLatestRunningProvisionTask(String tenantId) {
        return extTenantOpsTaskMapper.selectLatestRunningProvisionTask(tenantId);
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
                DataSource ds = createDataSource(config);
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
        DataSource ds = createDataSource(config);
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
        recordAudit(operatorId, "TENANT_ORG_ID_UPDATE", tenantId, "SUCCESS", "orgId=" + normalizedOrgId, 0L);
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

    @NonNull
    private DataSource createDataSource(TenantDbConfigDTO config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getJdbcUrl());
        dataSource.setUsername(config.getDbUsername());
        dataSource.setPassword(config.getDbPassword());
        dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setMaximumPoolSize(2);
        return dataSource;
    }

    private void closeIfPossible(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
            }
        }
    }
}
