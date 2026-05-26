package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.job;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeStatDayBuildService;
import cn.cordys.quartz.anno.QuartzScheduled;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
public class EmployeeStatDayBuildJob {

    @Resource
    private EmployeeStatDayBuildService employeeStatDayBuildService;
    @Resource
    private TenantTaskExecutor tenantTaskExecutor;

    @QuartzScheduled(cron = "0 20 0 * * ?")
    public void execute() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        if (LocalDate.of(2026, 5, 26).equals(targetDate)) {
            return;
        }
        tenantTaskExecutor.runForEachEnabledTenant("EmployeeStatDayBuildJob.execute", tenantId -> {
            log.info("开始重算员工分析日报，tenantId={}, statDate={}", tenantId, targetDate);
            employeeStatDayBuildService.rebuildDay(targetDate, "system");
        });
    }
}
