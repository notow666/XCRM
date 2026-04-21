package cn.cordys.crm.customer.job;

import cn.cordys.common.schedule.BaseScheduleJob;
import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.crm.customer.service.CustomerDataCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomerDataCleanupExecuteJob extends BaseScheduleJob {

    private static CustomerDataCleanupService customerDataCleanupService;
    private static TenantTaskExecutor tenantTaskExecutor;

    @Autowired
    public void init(CustomerDataCleanupService customerDataCleanupService, TenantTaskExecutor tenantTaskExecutor) {
        CustomerDataCleanupExecuteJob.customerDataCleanupService = customerDataCleanupService;
        CustomerDataCleanupExecuteJob.tenantTaskExecutor = tenantTaskExecutor;
    }

    @Override
    protected void businessExecute(JobExecutionContext context) {
        tenantTaskExecutor.runForEachEnabledTenant("CustomerDataCleanupExecuteJob.execute", tenantId -> {
            log.info("执行客户数据清理任务，tenantId={}", tenantId);
            try {
                customerDataCleanupService.executeCleanup();
            } catch (Exception e) {
                log.error("客户数据清理任务执行异常，tenantId={}", tenantId, e);
            }
        });
    }
}
