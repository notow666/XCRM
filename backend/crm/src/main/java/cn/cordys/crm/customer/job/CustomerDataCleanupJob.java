package cn.cordys.crm.customer.job;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.common.schedule.ScheduleManager;
import cn.cordys.crm.customer.domain.SysJobCronDetail;
import cn.cordys.crm.customer.service.CustomerDataCleanupService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.quartz.anno.QuartzScheduled;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CustomerDataCleanupJob {

    private static final String METHOD_NAME = "CustomerDataCleanupJob.executeCleanup";
    private static final String JOB_KEY_NAME = "customer_data_cleanup";
    private static final String JOB_GROUP = "customer_data_cleanup";
    private static final String TRIGGER_KEY_NAME = "customer_data_cleanup_trigger";
    private static final String TRIGGER_GROUP = "customer_data_cleanup_trigger";

    @Autowired
    private CustomerDataCleanupService customerDataCleanupService;

    @Autowired
    private TenantTaskExecutor tenantTaskExecutor;

    @Resource
    private ScheduleManager scheduleManager;

    @Resource
    private BaseMapper<SysJobCronDetail> sysJobCronDetailMapper;

    private final Map<String, String> tenantCronCache = new ConcurrentHashMap<>();

    @QuartzScheduled(cron = "0 */5 * * * ?")
    public void execute() {
        tenantTaskExecutor.runForEachEnabledTenant("CustomerDataCleanupJob.execute", tenantId -> {
            try {
                SysJobCronDetail jobConfig = loadJobConfig();
                if (jobConfig == null || Boolean.FALSE.equals(jobConfig.getEnable())) {
                    removeDynamicTrigger(tenantId);
                    tenantCronCache.remove(tenantId);
                    log.info("客户数据清理任务未启用或配置不存在，tenantId={}", tenantId);
                    return;
                }

                String currentCron = jobConfig.getCron();
                String cachedCron = tenantCronCache.get(tenantId);

                if (currentCron != null && !currentCron.equals(cachedCron)) {
                    log.info("检测到cron表达式变化，重新调度，tenantId={}, cron={}", tenantId, currentCron);
                    scheduleDynamicTrigger(tenantId, currentCron);
                    tenantCronCache.put(tenantId, currentCron);
                }
            } catch (Exception e) {
                log.error("客户数据清理任务检查调度异常，tenantId={}", tenantId, e);
            }
        });
    }



    private void scheduleDynamicTrigger(String tenantId, String cron) {
        try {
            JobKey jobKey = new JobKey(JOB_KEY_NAME, JOB_GROUP);
            TriggerKey triggerKey = new TriggerKey(TRIGGER_KEY_NAME, TRIGGER_GROUP);
            scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, CustomerDataCleanupExecuteJob.class, cron);
            log.info("客户数据清理动态触发器已调度，tenantId={}, cron={}", tenantId, cron);
        } catch (Exception e) {
            log.error("客户数据清理动态触发器调度失败，tenantId={}, cron={}", tenantId, cron, e);
        }
    }

    private void removeDynamicTrigger(String tenantId) {
        try {
            JobKey jobKey = new JobKey(JOB_KEY_NAME, JOB_GROUP);
            TriggerKey triggerKey = new TriggerKey(TRIGGER_KEY_NAME, TRIGGER_GROUP);
            scheduleManager.removeJob(jobKey, triggerKey);
            log.info("客户数据清理动态触发器已移除，tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("客户数据清理动态触发器移除失败，tenantId={}", tenantId, e);
        }
    }

    private SysJobCronDetail loadJobConfig() {
        LambdaQueryWrapper<SysJobCronDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysJobCronDetail::getMethodName, METHOD_NAME);
        List<SysJobCronDetail> list = sysJobCronDetailMapper.selectListByLambda(wrapper);
        return list != null && !list.isEmpty() ? list.getFirst() : null;
    }
}
