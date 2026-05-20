package cn.cordys.platform.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformTenantProvisionTaskExecutor {

    @Resource
    private PlatformAdminService platformAdminService;

    @Async(ExecutorBeanNames.MAIN_ASYNC)
    public void executeProvisionTask(String taskId, String tenantCode, String tenantName, String operatorId,
                                     List<String> initialUserIds, String orgId) {
        platformAdminService.executeProvisionTaskInternal(taskId, tenantCode, tenantName, operatorId, initialUserIds, orgId);
    }
}
