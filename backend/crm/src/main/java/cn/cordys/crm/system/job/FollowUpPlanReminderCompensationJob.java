package cn.cordys.crm.system.job;

import cn.cordys.crm.follow.service.FollowUpPlanReminderService;
import cn.cordys.quartz.anno.QuartzScheduled;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FollowUpPlanReminderCompensationJob {

    @Resource
    private FollowUpPlanReminderService followUpPlanReminderService;

    @QuartzScheduled(cron = "0 0/10 * * * ?")
    public void execute() {
        log.info("执行跟进计划提醒补偿任务");
        followUpPlanReminderService.compensateAllTenants();
    }
}
