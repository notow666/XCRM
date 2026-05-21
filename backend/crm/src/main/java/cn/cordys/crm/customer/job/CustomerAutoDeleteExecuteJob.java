package cn.cordys.crm.customer.job;

import cn.cordys.common.schedule.BaseScheduleJob;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.customer.service.CustomerAutoDeleteService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 租户客户定时删除执行 Job（由租户库 {@code schedule} 表同步到 master Quartz）。
 */
@Component
@Slf4j
public class CustomerAutoDeleteExecuteJob extends BaseScheduleJob {

    private static CustomerAutoDeleteService customerAutoDeleteService;

    @Autowired
    public void init(CustomerAutoDeleteService customerAutoDeleteService) {
        CustomerAutoDeleteExecuteJob.customerAutoDeleteService = customerAutoDeleteService;
    }

    @Override
    protected void businessExecute(JobExecutionContext context) {
        try {
            log.info("执行客户定时删除任务，tenantId={}", TenantContext.getTenantId());
            customerAutoDeleteService.executeAutoDelete();
        } catch (Exception e) {
            log.error("客户定时删除任务执行异常，tenantId={}", TenantContext.getTenantId(), e);
        }
    }
}
