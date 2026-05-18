package cn.cordys.crm.customer.job;

import cn.cordys.common.schedule.BaseScheduleJob;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.customer.service.CustomerDataCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 租户客户数据清理执行 Job（由租户库 {@code schedule} 表同步到 master Quartz）。
 * <p>tenantId 由 {@link BaseScheduleJob} 从 JobDataMap / JobKey 前缀恢复，仅清理当前租户。</p>
 */
@Component
@Slf4j
public class CustomerDataCleanupExecuteJob extends BaseScheduleJob {

    private static CustomerDataCleanupService customerDataCleanupService;

    @Autowired
    public void init(CustomerDataCleanupService customerDataCleanupService) {
        CustomerDataCleanupExecuteJob.customerDataCleanupService = customerDataCleanupService;
    }

    @Override
    protected void businessExecute(JobExecutionContext context) {
        log.info("执行客户数据清理任务，tenantId={}", TenantContext.getTenantId());
        try {
            customerDataCleanupService.executeCleanup();
        } catch (Exception e) {
            log.error("客户数据清理任务执行异常，tenantId={}", TenantContext.getTenantId(), e);
        }
    }
}
