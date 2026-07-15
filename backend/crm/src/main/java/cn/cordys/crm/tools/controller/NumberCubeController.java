package cn.cordys.crm.tools.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.tools.dto.request.NumberCubeSegmentQueryRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskCreateRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskPageRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskProgressBatchRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskSegmentDetailRequest;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadProgressResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadStartResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskDetailResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskProgressResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskSegmentDetailResponse;
import cn.cordys.crm.tools.service.NumberCubeTaskService;
import cn.cordys.platform.dto.response.PhoneSegmentRegionNodeResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentResponse;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tools/number-cube")
@Tag(name = "工具-号码魔方")
public class NumberCubeController {

    @Resource
    private NumberCubeTaskService numberCubeTaskService;

    @PostMapping("/page")
    @Operation(summary = "号码魔方任务分页")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public PagerWithOption<List<NumberCubeTaskResponse>> page(@Valid @RequestBody NumberCubeTaskPageRequest request) {
        return numberCubeTaskService.page(request, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/add")
    @Operation(summary = "创建号码魔方任务")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_ADD)
    public NumberCubeTaskResponse add(@Valid @RequestBody NumberCubeTaskCreateRequest request) {
        return numberCubeTaskService.create(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "号码魔方任务详情")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public NumberCubeTaskDetailResponse detail(@PathVariable("id") String id) {
        return numberCubeTaskService.detail(id, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/detail/segments")
    @Operation(summary = "号码魔方任务号段明细(按prefix)")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public NumberCubeTaskSegmentDetailResponse segmentDetail(@Valid @RequestBody NumberCubeTaskSegmentDetailRequest request) {
        return numberCubeTaskService.segmentDetail(request, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/delete/{id}")
    @Operation(summary = "删除号码魔方任务")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_DELETE)
    public void delete(@PathVariable("id") String id) {
        numberCubeTaskService.delete(id, OrganizationContext.getOrganizationId());
    }

    @GetMapping("/regions")
    @Operation(summary = "号段省市级联数据")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public List<PhoneSegmentRegionNodeResponse> regions() {
        return numberCubeTaskService.listRegions();
    }

    @PostMapping("/segments/groups")
    @Operation(summary = "按省市查询号段分组(前三位)")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public List<PlatformPhoneSegmentGroupResponse> segmentGroups(@Valid @RequestBody NumberCubeSegmentQueryRequest request) {
        return numberCubeTaskService.listSegmentGroups(request.getProvince(), request.getCity());
    }

    @PostMapping("/segments")
    @Operation(summary = "按省市查询号段")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public List<PlatformPhoneSegmentResponse> segments(@Valid @RequestBody NumberCubeSegmentQueryRequest request) {
        return numberCubeTaskService.listSegments(request.getProvince(), request.getCity(), request.getPrefix());
    }

    @GetMapping("/progress/{id}")
    @Operation(summary = "号码魔方任务进度")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public NumberCubeTaskProgressResponse progress(@PathVariable("id") String id) {
        return numberCubeTaskService.getProgress(id, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/progress/batch")
    @Operation(summary = "号码魔方任务进度(批量)")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_READ)
    public List<NumberCubeTaskProgressResponse> progressBatch(@Valid @RequestBody NumberCubeTaskProgressBatchRequest request) {
        return numberCubeTaskService.getProgressBatch(request, OrganizationContext.getOrganizationId());
    }

    @GetMapping("/download/progress/{exportJobId}")
    @Operation(summary = "号码魔方打包进度")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_DOWNLOAD)
    public NumberCubeDownloadProgressResponse downloadProgress(@PathVariable("exportJobId") String exportJobId,
                                                               @RequestParam("taskId") String taskId) {
        return numberCubeTaskService.downloadProgress(taskId, exportJobId, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @GetMapping("/download/file/{exportJobId}")
    @Operation(summary = "号码魔方打包结果文件下载")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_DOWNLOAD)
    public void downloadFile(@PathVariable("exportJobId") String exportJobId,
                             @RequestParam("taskId") String taskId,
                             HttpServletResponse response) {
        numberCubeTaskService.downloadFile(taskId, exportJobId, OrganizationContext.getOrganizationId(), SessionUtils.getUserId(), response);
    }

    @GetMapping("/download/cached/{id}")
    @Operation(summary = "号码魔方缓存打包文件下载")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_DOWNLOAD)
    public void downloadCached(@PathVariable("id") String id,
                               @RequestParam(value = "maskMode", defaultValue = "PLAIN") String maskMode,
                               HttpServletResponse response) {
        numberCubeTaskService.downloadCached(id, OrganizationContext.getOrganizationId(), maskMode, response);
    }

    @PostMapping("/download/start/{id}")
    @Operation(summary = "启动号码魔方异步打包下载")
    @RequiresPermissions(PermissionConstants.NUMBER_CUBE_DOWNLOAD)
    public NumberCubeDownloadStartResponse downloadStart(@PathVariable("id") String id,
                                                         @RequestParam(value = "maskMode", defaultValue = "PLAIN") String maskMode) {
        return numberCubeTaskService.downloadStart(id, OrganizationContext.getOrganizationId(), maskMode, SessionUtils.getUserId());
    }
}
