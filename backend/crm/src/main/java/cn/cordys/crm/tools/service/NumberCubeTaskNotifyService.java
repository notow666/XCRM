package cn.cordys.crm.tools.service;

import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.crm.tools.constants.NumberCubeConstants;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskProgressResponse;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NumberCubeTaskNotifyService {

    @Resource
    private SseService sseService;

    public void publishTaskUpdated(NumberCubeTask task, NumberCubeTaskProgressResponse progress) {
        if (task == null || StringUtils.isBlank(task.getCreateUser())) {
            return;
        }
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", NumberCubeConstants.SSE_TASK_UPDATED);
        payload.put("taskId", task.getId());
        payload.put("status", task.getStatus());
        payload.put("errorMessage", task.getErrorMessage());
        if (progress != null) {
            payload.put("processed", progress.getProcessed());
            payload.put("total", progress.getTotal());
        }
        sseService.publishTenantUserCustomEvent(tenantId, task.getCreateUser(),
                NumberCubeConstants.SSE_TASK_UPDATED, payload);
    }
}
