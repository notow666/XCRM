package cn.cordys.crm.system.job;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.crm.system.job.listener.ExecuteEvent;
import cn.cordys.quartz.anno.QuartzScheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskCleanupJob {

    private final ApplicationEventPublisher publisher;
    private final TenantTaskExecutor tenantTaskExecutor;

    @Autowired
    public TaskCleanupJob(ApplicationEventPublisher publisher, TenantTaskExecutor tenantTaskExecutor) {
        this.publisher = publisher;
        this.tenantTaskExecutor = tenantTaskExecutor;
    }

    /**
     * 系统定时任务执行
     * <p>
     * 该方法每晚 3 点执行，清理过期的任务和释放资源。
     * </p>
     */
    @QuartzScheduled(cron = "0 0 3 * * ?")
    public void execute() {
        tenantTaskExecutor.runForEachEnabledTenant("TaskCleanupJob.execute", tenantId -> {
            log.info("执行清理任务，tenantId={}", tenantId);
            runAll();
        });
    }


    public void runAll() {
        log.info("开始执行所有清理任务");
        publisher.publishEvent(new ExecuteEvent(this));
        log.info("所有清理任务执行完成");
    }

}
