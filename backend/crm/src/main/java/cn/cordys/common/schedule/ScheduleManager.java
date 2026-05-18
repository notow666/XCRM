package cn.cordys.common.schedule;

import cn.cordys.common.exception.GenericException;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.domain.Schedule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;

/**
 * 定时任务管理器，用于管理调度任务的添加、修改、删除等操作。
 * <p>Quartz JobStore 使用 crm_master；租户通过 JobKey 前缀与 JobDataMap.tenantId 隔离。</p>
 */
@Slf4j
public class ScheduleManager {
    public static final String TENANT_ID_KEY = "tenantId";

    @Resource
    private Scheduler scheduler;

    public static void startJobs(Scheduler schedule) {
        try {
            schedule.start();
        } catch (Exception e) {
            log.error("启动调度器失败", e);
            throw new RuntimeException("启动调度器失败", e);
        }
    }

    public void addSimpleJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> cls, int repeatIntervalTime, JobDataMap jobDataMap)
            throws SchedulerException {

        JobBuilder jobBuilder = JobBuilder.newJob(cls).withIdentity(withTenantJobKey(jobKey));
        jobBuilder.usingJobData(ensureTenantJobDataMap(jobDataMap));

        SimpleTrigger trigger = TriggerBuilder.newTrigger().withIdentity(withTenantTriggerKey(triggerKey))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(repeatIntervalTime).repeatForever())
                .startNow().build();

        scheduler.scheduleJob(jobBuilder.build(), trigger);
    }

    public void addCronJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> jobClass, String cron, JobDataMap jobDataMap) {
        try {
            log.info("addCronJob: {},{}", triggerKey.getName(), triggerKey.getGroup());
            JobDataMap map = ensureTenantJobDataMap(jobDataMap);
            JobBuilder jobBuilder = JobBuilder.newJob(jobClass).withIdentity(withTenantJobKey(jobKey)).usingJobData(map);

            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();
            triggerBuilder.withIdentity(withTenantTriggerKey(triggerKey));
            triggerBuilder.startNow();
            triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cron));
            CronTrigger trigger = (CronTrigger) triggerBuilder.build();
            scheduler.scheduleJob(jobBuilder.build(), trigger);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new GenericException("定时任务配置异常: " + e.getMessage(), e);
        }
    }

    public void addCronJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> jobClass, String cron) {
        addCronJob(jobKey, triggerKey, jobClass, cron, null);
    }

    public void modifyCronJobTime(TriggerKey triggerKey, String cron) {
        TriggerKey tenantTriggerKey = withTenantTriggerKey(triggerKey);

        log.info("modifyCronJobTime: {}", tenantTriggerKey.getName() + "," + tenantTriggerKey.getGroup());
        try {
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(tenantTriggerKey);
            if (trigger == null) {
                return;
            }

            String oldTime = trigger.getCronExpression();
            if (!oldTime.equalsIgnoreCase(cron)) {
                TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();
                triggerBuilder.withIdentity(tenantTriggerKey);
                triggerBuilder.startNow();
                triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cron));
                trigger = (CronTrigger) triggerBuilder.build();
                scheduler.rescheduleJob(tenantTriggerKey, trigger);
            }
        } catch (Exception e) {
            throw new RuntimeException("修改 Cron 表达式失败", e);
        }
    }

    public void removeJob(JobKey jobKey, TriggerKey triggerKey) {
        JobKey tenantJobKey = withTenantJobKey(jobKey);
        TriggerKey tenantTriggerKey = withTenantTriggerKey(triggerKey);
        try {
            log.info("RemoveJob: {},{}", tenantJobKey.getName(), tenantJobKey.getGroup());
            scheduler.pauseTrigger(tenantTriggerKey);
            scheduler.unscheduleJob(tenantTriggerKey);
            scheduler.deleteJob(tenantJobKey);
        } catch (Exception e) {
            log.error("删除任务失败", e);
            throw new RuntimeException("删除任务失败", e);
        }
    }

    public void shutdownJobs(Scheduler schedule) {
        try {
            if (!schedule.isShutdown()) {
                schedule.shutdown();
            }
        } catch (Exception e) {
            log.error("关闭调度器失败", e);
            throw new RuntimeException("关闭调度器失败", e);
        }
    }

    public void addOrUpdateCronJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> jobClass, String cron, JobDataMap jobDataMap)
            throws SchedulerException {
        JobKey tenantJobKey = withTenantJobKey(jobKey);
        TriggerKey tenantTriggerKey = withTenantTriggerKey(triggerKey);
        JobDataMap map = ensureTenantJobDataMap(jobDataMap);
        log.info("AddOrUpdateCronJob: {}", tenantJobKey.getName() + "," + tenantTriggerKey.getGroup());

        if (scheduler.checkExists(tenantTriggerKey)) {
            modifyCronJobTime(triggerKey, cron);
            replaceJobDataMap(tenantJobKey, jobClass, map);
        } else {
            addCronJob(jobKey, triggerKey, jobClass, cron, map);
        }
    }

    public void addOrUpdateCronJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> jobClass, String cron) throws SchedulerException {
        addOrUpdateCronJob(jobKey, triggerKey, jobClass, cron, null);
    }

    public boolean triggerExists(TriggerKey triggerKey) throws SchedulerException {
        return scheduler.checkExists(withTenantTriggerKey(triggerKey));
    }

    public JobDataMap buildTenantJobDataMap(String tenantId, JobDataMap extra) {
        JobDataMap jobDataMap = extra != null ? new JobDataMap(extra) : new JobDataMap();
        jobDataMap.put(TENANT_ID_KEY, tenantId);
        return jobDataMap;
    }

    public JobDataMap getDefaultJobDataMap(Schedule schedule, String expression, String userId) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("resourceId", schedule.getResourceId());
        jobDataMap.put("expression", expression);
        jobDataMap.put("userId", userId);
        jobDataMap.put("config", schedule.getConfig());
        jobDataMap.put("organizationId", schedule.getOrganizationId());
        jobDataMap.put(TENANT_ID_KEY, TenantContext.requireTenantId());
        return jobDataMap;
    }

    private JobDataMap ensureTenantJobDataMap(JobDataMap jobDataMap) {
        JobDataMap map = jobDataMap != null ? new JobDataMap(jobDataMap) : new JobDataMap();
        if (StringUtils.isBlank(map.getString(TENANT_ID_KEY))) {
            map.put(TENANT_ID_KEY, TenantContext.requireTenantId());
        }
        return map;
    }

    private void replaceJobDataMap(JobKey tenantJobKey, Class<? extends Job> jobClass, JobDataMap jobDataMap) {
        try {
            if (!scheduler.checkExists(tenantJobKey)) {
                return;
            }
            JobDetail detail = JobBuilder.newJob(jobClass)
                    .withIdentity(tenantJobKey)
                    .usingJobData(jobDataMap)
                    .storeDurably(false)
                    .build();
            scheduler.addJob(detail, true, true);
        } catch (SchedulerException e) {
            throw new RuntimeException("更新 JobDataMap 失败", e);
        }
    }

    private JobKey withTenantJobKey(JobKey jobKey) {
        String tenantId = TenantContext.requireTenantId();
        String name = TenantQuartzKeys.appendTenantPrefix(jobKey.getName(), tenantId);
        String group = TenantQuartzKeys.appendTenantPrefix(jobKey.getGroup(), tenantId);
        return new JobKey(name, group);
    }

    private TriggerKey withTenantTriggerKey(TriggerKey triggerKey) {
        String tenantId = TenantContext.requireTenantId();
        String name = TenantQuartzKeys.appendTenantPrefix(triggerKey.getName(), tenantId);
        String group = TenantQuartzKeys.appendTenantPrefix(triggerKey.getGroup(), tenantId);
        return new TriggerKey(name, group);
    }
}
