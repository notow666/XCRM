package cn.cordys.mmba.job;

import cn.cordys.common.schedule.BaseScheduleJob;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.service.MmbaCallLogCleanService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 租户通话记录定时清除任务，由租户 schedule 表同步到 Quartz。 */
@Slf4j
@Component
public class MmbaCallLogCleanExecuteJob extends BaseScheduleJob {

    private static MmbaCallLogCleanService callLogCleanService;

    @Autowired
    public void init(MmbaCallLogCleanService service) {
        MmbaCallLogCleanExecuteJob.callLogCleanService = service;
    }

    @Override
    protected void businessExecute(JobExecutionContext context) {
        // Quartz 线程没有 Web 请求上下文，组织 ID 必须从 schedule 同步进来的 JobDataMap 读取。
        String organizationId = context.getMergedJobDataMap().getString("organizationId");
        try {
            log.info("执行定时清除通话记录任务 tenantId={} organizationId={}", TenantContext.getTenantId(), organizationId);
            callLogCleanService.executeScheduled(organizationId);
        } catch (Exception e) {
            log.error("定时清除通话记录任务执行异常 tenantId={} organizationId={}", TenantContext.getTenantId(), organizationId, e);
        }
    }
}
