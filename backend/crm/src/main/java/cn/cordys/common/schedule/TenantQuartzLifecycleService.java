package cn.cordys.common.schedule;

import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.service.ExtScheduleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 租户开通/启用/冻结/删除时，与 crm_master Quartz 的同步与清理。
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "quartz", name = "enabled", havingValue = "true")
public class TenantQuartzLifecycleService {

    @Resource
    private TenantScheduleRegistry tenantScheduleRegistry;

    @Resource
    private ExtScheduleService extScheduleService;

    /**
     * 新租户开通或重新启用后：将租户库中已启用的 schedule 注册到 master Quartz。
     */
    public void initializeTenantSchedules(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        log.info("初始化租户 Quartz 任务，tenantId={}", tenantId);
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            extScheduleService.syncEnabledSchedulesForCurrentTenant();
        } catch (Exception e) {
            log.error("初始化租户 Quartz 任务失败，tenantId={}", tenantId, e);
        } finally {
            restoreTenantContext(previous);
        }
    }

    /**
     * 租户冻结、删除或开通失败回滚：移除 master 中该租户全部 Quartz Job/Trigger。
     */
    public void purgeTenantSchedules(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        log.info("清理租户 Quartz 任务，tenantId={}", tenantId);
        tenantScheduleRegistry.purgeAllJobsForTenant(tenantId);
    }

    private static void restoreTenantContext(String previousTenantId) {
        if (StringUtils.isBlank(previousTenantId)) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(previousTenantId);
        }
    }
}
