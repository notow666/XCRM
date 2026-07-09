package cn.cordys.tenant.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
import cn.cordys.context.ActiveDbRole;
import cn.cordys.context.ShadowMaintenanceState;
import cn.cordys.tenant.dto.TenantShadowMetaDTO;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 租户主库 A 与影子库 B 切换编排。
 */
@Slf4j
@Service
public class TenantShadowSwitchService {

    private static final long PRE_NOTICE_MS = 30_000L;
    private static final long BATCH_TASK_POLL_MS = 3_000L;
    private static final long BATCH_TASK_MAX_WAIT_MS = 600_000L;

    @Resource
    private ExtTenantMapper extTenantMapper;

    @Resource
    private TenantShadowMetaService tenantShadowMetaService;

    @Resource
    private TenantShadowProvisioningService tenantShadowProvisioningService;

    @Resource
    private TenantIdentitySyncService tenantIdentitySyncService;

    @Resource
    private TenantShadowCacheEvictionService tenantShadowCacheEvictionService;

    @Resource
    private TenantShadowBatchTaskGuard tenantShadowBatchTaskGuard;

    public TenantShadowMetaDTO getShadowStatus(String tenantId) {
        return tenantShadowMetaService.getShadowMeta(tenantId);
    }

    /**
     * 异步执行 A→B 切换（30s 预告 → 维护窗 → 增量 sync → 切 role）。
     */
    @Async
    public void switchToShadowAsync(String tenantId, String operatorId) {
        switchToShadowInternal(tenantId, operatorId);
    }

    public void switchToShadowInternal(String tenantId, String operatorId) {
        tenantId = requireShadowEnabled(tenantId);
        tenantShadowProvisioningService.ensureShadowDataSourceRegistered(tenantId);

        long now = System.currentTimeMillis();
        extTenantMapper.updateShadowMaintenance(tenantId, ShadowMaintenanceState.PRE_NOTICE.name(),
                now + PRE_NOTICE_MS, now, operatorId);
        tenantShadowMetaService.evictShadowMetaCache(tenantId);
        tenantShadowCacheEvictionService.broadcastPreNotice(tenantId);

        sleepQuietly(PRE_NOTICE_MS);

        now = System.currentTimeMillis();
        extTenantMapper.updateShadowMaintenance(tenantId, ShadowMaintenanceState.BLOCKING.name(),
                null, now, operatorId);
        tenantShadowMetaService.evictShadowMetaCache(tenantId);

        waitUntilBatchTasksDone(tenantId);

        tenantIdentitySyncService.incrementalSyncToShadow(tenantId);

        now = System.currentTimeMillis();
        extTenantMapper.updateActiveDbRole(tenantId, ActiveDbRole.SHADOW.name(), now, operatorId);
        extTenantMapper.clearShadowMaintenance(tenantId, now, operatorId);
        tenantShadowMetaService.evictShadowMetaCache(tenantId);
        tenantShadowCacheEvictionService.evictTenantBusinessCache(tenantId);
        tenantShadowCacheEvictionService.broadcastSwitchComplete(tenantId);
        log.info("[TENANT_SWITCH_TO_SHADOW] tenantId={}, operator={}", tenantId, operatorId);
    }

    public void switchToPrimary(String tenantId, String operatorId) {
        tenantId = requireShadowEnabled(tenantId);
        long now = System.currentTimeMillis();
        extTenantMapper.updateActiveDbRole(tenantId, ActiveDbRole.PRIMARY.name(), now, operatorId);
        tenantShadowMetaService.evictShadowMetaCache(tenantId);
        tenantShadowCacheEvictionService.evictTenantBusinessCache(tenantId);
        tenantShadowCacheEvictionService.broadcastSwitchComplete(tenantId);
        log.info("[TENANT_SWITCH_TO_PRIMARY] tenantId={}, operator={}", tenantId, operatorId);
    }

    private String requireShadowEnabled(String tenantId) {
        tenantId = StringUtils.trimToNull(tenantId);
        if (tenantId == null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "tenantId 不能为空");
        }
        if (!tenantShadowMetaService.isShadowEnabled(tenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get("tenant.shadow.not.enabled"));
        }
        return tenantId;
    }

    private void waitUntilBatchTasksDone(String tenantId) {
        long deadline = System.currentTimeMillis() + BATCH_TASK_MAX_WAIT_MS;
        while (tenantShadowBatchTaskGuard.hasRunningBatchTasks(tenantId)) {
            if (System.currentTimeMillis() > deadline) {
                throw new GenericException(CrmHttpResultCode.FAILED, Translator.get("tenant.shadow.batch.task.timeout"));
            }
            sleepQuietly(BATCH_TASK_POLL_MS);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenericException(CrmHttpResultCode.FAILED, "切换被中断");
        }
    }
}
