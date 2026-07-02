package cn.cordys.platform.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class PlatformTenantDataCleanupTaskExecutor {

    @Resource
    private PlatformTenantDataCleanupService platformTenantDataCleanupService;

    @Async(ExecutorBeanNames.MAIN_ASYNC)
    public void executeCleanupTask(String taskId, String tenantId, String operatorId, LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        log.info("[租户数据清理-异步任务开始] 任务ID={}, 租户={}, 操作人={}, 开始日期={}, 结束日期={}",
                taskId, tenantId, operatorId, startDate, endDate);
        try {
            platformTenantDataCleanupService.executeTask(taskId, tenantId, operatorId, startDate, endDate);
        } finally {
            log.info("[租户数据清理-异步任务结束] 任务ID={}, 租户={}, 开始日期={}, 结束日期={}, 耗时毫秒={}",
                    taskId, tenantId, startDate, endDate, System.currentTimeMillis() - start);
        }
    }
}
