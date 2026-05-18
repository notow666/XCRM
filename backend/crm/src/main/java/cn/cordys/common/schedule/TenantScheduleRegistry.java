package cn.cordys.common.schedule;

import cn.cordys.context.TenantContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 租户定时任务在 crm_master Quartz 中的标准注册入口。
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "quartz", name = "enabled", havingValue = "true")
public class TenantScheduleRegistry {

    @Resource
    private ScheduleManager scheduleManager;

    @Resource
    private Scheduler scheduler;

    public void runWithTenant(String tenantId, Runnable action) {
        runWithTenantResult(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public boolean triggerExists(String tenantId, TriggerKey triggerKey) {
        return Boolean.TRUE.equals(runWithTenantResult(tenantId, () -> {
            try {
                return scheduler.checkExists(toTenantTriggerKey(triggerKey));
            } catch (SchedulerException e) {
                throw new IllegalStateException("检查 Quartz Trigger 失败", e);
            }
        }));
    }

    public void registerOrUpdateCronJob(String tenantId, JobKey jobKey, TriggerKey triggerKey,
                                        Class<? extends Job> jobClass, String cron, JobDataMap jobDataMap) {
        runWithTenant(tenantId, () -> {
            try {
                JobDataMap map = scheduleManager.buildTenantJobDataMap(tenantId, jobDataMap);
                scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, jobClass, cron, map);
            } catch (SchedulerException e) {
                throw new IllegalStateException("注册 Quartz 任务失败, tenantId=" + tenantId, e);
            }
        });
    }

    public void removeJob(String tenantId, JobKey jobKey, TriggerKey triggerKey) {
        runWithTenant(tenantId, () -> scheduleManager.removeJob(jobKey, triggerKey));
    }

    /**
     * 删除 master Quartz 中该租户前缀下的全部 Job/Trigger（冻结、下线、开通失败回滚）。
     */
    public void purgeAllJobsForTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        String prefix = TenantQuartzKeys.tenantPrefix(tenantId);
        runWithTenant(tenantId, () -> {
            try {
                Set<JobKey> toDelete = new HashSet<>();
                for (String groupName : scheduler.getJobGroupNames()) {
                    if (groupName != null && groupName.startsWith(prefix)) {
                        toDelete.addAll(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName)));
                    }
                }
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.anyJobGroup())) {
                    if (jobKey.getName().startsWith(prefix) || jobKey.getGroup().startsWith(prefix)) {
                        toDelete.add(jobKey);
                    }
                }
                for (JobKey jobKey : toDelete) {
                    try {
                        for (Trigger trigger : scheduler.getTriggersOfJob(jobKey)) {
                            TriggerKey triggerKey = trigger.getKey();
                            scheduler.pauseTrigger(triggerKey);
                            scheduler.unscheduleJob(triggerKey);
                        }
                        scheduler.deleteJob(jobKey);
                        log.info("已删除租户 Quartz Job，tenantId={}, jobKey={}", tenantId, jobKey);
                    } catch (SchedulerException e) {
                        log.warn("删除租户 Quartz Job 失败，tenantId={}, jobKey={}", tenantId, jobKey, e);
                    }
                }
            } catch (SchedulerException e) {
                log.error("清理租户 Quartz 任务失败，tenantId={}", tenantId, e);
            }
        });
    }

    private TriggerKey toTenantTriggerKey(TriggerKey triggerKey) {
        String tenantId = TenantContext.requireTenantId();
        return new TriggerKey(
                TenantQuartzKeys.appendTenantPrefix(triggerKey.getName(), tenantId),
                TenantQuartzKeys.appendTenantPrefix(triggerKey.getGroup(), tenantId)
        );
    }

    private <T> T runWithTenantResult(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.isBlank(previousTenantId)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
        }
    }
}
