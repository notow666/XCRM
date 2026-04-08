package cn.cordys.crm.task.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.follow.dto.request.FollowUpPlanPageRequest;
import cn.cordys.crm.follow.dto.response.FollowUpPlanListResponse;
import cn.cordys.crm.task.dto.request.TaskFollowPlanCompleteRequest;
import cn.cordys.crm.task.service.TaskService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "任务（跟进计划待办）")
@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private TaskService taskService;

    @PostMapping("/follow/plan/page")
    @RequiresPermissions(PermissionConstants.TASK_READ)
    @Operation(summary = "任务列表（我的客户跟进计划待办）")
    public PagerWithOption<List<FollowUpPlanListResponse>> pageFollowPlans(@Validated @RequestBody FollowUpPlanPageRequest request) {
        return taskService.pageTaskFollowPlans(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/follow/plan/complete")
    @RequiresPermissions(PermissionConstants.TASK_COMPLETE)
    @Operation(summary = "完成任务（将跟进计划置为已完成）")
    public void completeFollowPlan(@Validated @RequestBody TaskFollowPlanCompleteRequest request) {
        taskService.completeFollowPlan(request.getId(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
