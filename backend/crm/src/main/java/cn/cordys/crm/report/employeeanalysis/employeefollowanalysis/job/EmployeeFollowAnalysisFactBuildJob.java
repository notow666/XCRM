package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.job;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeFollowAnalysisFactBuildService;
import cn.cordys.quartz.anno.QuartzScheduled;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
public class EmployeeFollowAnalysisFactBuildJob {

    @Resource
    private EmployeeFollowAnalysisFactBuildService employeeFollowAnalysisFactBuildService;
    @Resource
    private TenantTaskExecutor tenantTaskExecutor;

    @QuartzScheduled(cron = "0 20 0 * * ?")
    public void execute() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        tenantTaskExecutor.runForEachEnabledTenant("EmployeeFollowAnalysisFactBuildJob.execute", tenantId -> {
            log.info("开始重算员工跟进分析事实日报，tenantId={}, statDate={}", tenantId, targetDate);
            employeeFollowAnalysisFactBuildService.rebuildDay(targetDate, "system");
        });
    }
}
