package cn.cordys.crm.customer.controller;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.ExportSelectRequest;
import cn.cordys.common.dto.chart.ChartResult;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.dto.CustomerPoolDTO;
import cn.cordys.crm.customer.dto.request.*;
import cn.cordys.crm.customer.dto.response.CustomerGetResponse;
import cn.cordys.crm.customer.dto.response.CustomerListResponse;
import cn.cordys.crm.customer.dto.response.PoolCustomerImportCheckResponse;
import cn.cordys.crm.customer.service.CustomerPoolExportService;
import cn.cordys.crm.customer.service.CustomerService;
import cn.cordys.crm.customer.service.PoolCustomerImportService;
import cn.cordys.crm.customer.service.PoolCustomerService;
import cn.cordys.crm.system.dto.request.PoolBatchAssignRequest;
import cn.cordys.crm.system.dto.request.PoolBatchPickRequest;
import cn.cordys.crm.system.dto.request.PoolBatchRequest;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "公海客户")
@RestController
@RequestMapping("/pool/account")
public class PoolCustomerController {

    @Resource
    private PoolCustomerService poolCustomerService;
    @Resource
    private CustomerService customerService;
    @Resource
    private CustomerPoolExportService customerPoolExportService;
    @Resource
    private PoolCustomerImportService poolCustomerImportService;

    @GetMapping("/options")
    @Operation(summary = "获取当前用户公海选项")
    public List<CustomerPoolDTO> getPoolOptions() {
        return poolCustomerService.getPoolOptions(SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/page")
    @Operation(summary = "客户列表")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_READ})
    public PagerWithOption<List<CustomerListResponse>> list(@Validated @RequestBody CustomerPageRequest request) {
        ConditionFilterUtils.parseCondition(request);
        return customerService.list(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId(), null);
    }

    @PostMapping("/pick")
    @Operation(summary = "领取客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_PICK})
    public void pick(@Validated @RequestBody PoolCustomerPickRequest request) {
        poolCustomerService.pick(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/assign")
    @Operation(summary = "分配客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_ASSIGN})
    public void assign(@Validated @RequestBody PoolCustomerAssignRequest request) {
        poolCustomerService.assign(request.getCustomerId(), request.getAssignUserId(), OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "删除客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_DELETE})
    public void delete(@PathVariable("id") String id) {
        poolCustomerService.delete(id);
    }

    @GetMapping("/get/{id}")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_READ)
    @Operation(summary = "客户详情")
    public CustomerGetResponse get(@PathVariable("id") String id) {
        return customerService.get(id);
    }

    @PostMapping("/batch-pick")
    @Operation(summary = "批量领取客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_PICK})
    public void batchPick(@Validated @RequestBody PoolBatchPickRequest request) {
        poolCustomerService.batchPick(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/batch-assign")
    @Operation(summary = "批量分配客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_ASSIGN})
    public void batchAssign(@Validated @RequestBody PoolBatchAssignRequest request) {
        poolCustomerService.batchAssign(request, request.getAssignUserId(), OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除客户")
    @RequiresPermissions(value = {PermissionConstants.CUSTOMER_MANAGEMENT_POOL_DELETE})
    public void batchDelete(@Validated @RequestBody PoolBatchRequest request) {
        poolCustomerService.batchDelete(request.getBatchIds(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/transfer")
    @Operation(summary = "转移客户到指定公海池")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_TRANSFER)
    public void transfer(@Validated @RequestBody PoolTransferRequest request) {
        poolCustomerService.transfer(request.getCustomerId(), request.getTargetPoolId(),
                SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/batch-transfer")
    @Operation(summary = "批量转移客户到指定公海池")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_TRANSFER)
    public void batchTransfer(@Validated @RequestBody PoolBatchTransferRequest request) {
        poolCustomerService.batchTransfer(request.getBatchIds(), request.getTargetPoolId(),
                SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/batch-update")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_UPDATE)
    @Operation(summary = "批量更新客户")
    public void batchUpdate(@Validated @RequestBody ResourceBatchEditRequest request) {
        poolCustomerService.batchUpdate(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/export-all")
    @Operation(summary = "客户导出全部")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_EXPORT)
    public String customerPoolExportAll(@Validated @RequestBody CustomerExportRequest request) {
        ConditionFilterUtils.parseCondition(request);
        return customerPoolExportService.exportCrossPage(SessionUtils.getUserId(), request, OrganizationContext.getOrganizationId(), null, LocaleContextHolder.getLocale());
    }

    @PostMapping("/export-select")
    @Operation(summary = "导出选中客户")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_EXPORT)
    public String customerPoolExportSelect(@Validated @RequestBody ExportSelectRequest request) {
        return customerPoolExportService.exportCrossSelect(SessionUtils.getUserId(), request, OrganizationContext.getOrganizationId(), LocaleContextHolder.getLocale());
    }

    @PostMapping("/chart")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_READ)
    @Operation(summary = "客户图表生成")
    public List<ChartResult> chart(@Validated @RequestBody PoolCustomerChartAnalysisRequest request) {
        return poolCustomerService.chart(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId(), null);
    }

    @GetMapping("/template/download")
    @Operation(summary = "下载公海导入模板")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_IMPORT)
    public void downloadImportTpl(HttpServletResponse response) {
        poolCustomerImportService.downloadImportTpl(response, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/import/pre-check")
    @Operation(summary = "公海导入预检查")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_IMPORT)
    public PoolCustomerImportCheckResponse preCheck(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("poolId") String poolId) {
        return poolCustomerImportService.preCheck(file, poolId, OrganizationContext.getOrganizationId(), SessionUtils.getUserId());
    }

    @PostMapping("/import")
    @Operation(summary = "公海导入")
    @RequiresPermissions(PermissionConstants.CUSTOMER_MANAGEMENT_POOL_IMPORT)
    public String realImport(@RequestParam("file") MultipartFile file,
                             @RequestParam("poolId") String poolId) {
        return poolCustomerImportService.realImport(file, poolId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/import/error-file/{fileId}")
    @Operation(summary = "下载公海导入错误文件")
    public void downloadErrorFile(@PathVariable("fileId") String fileId, HttpServletResponse response) {
        poolCustomerImportService.downloadErrorFile(fileId, OrganizationContext.getOrganizationId(), response);
    }
}