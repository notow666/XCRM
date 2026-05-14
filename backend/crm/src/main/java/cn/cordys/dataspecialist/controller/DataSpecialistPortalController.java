package cn.cordys.dataspecialist.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.customer.dto.CustomerPoolDTO;
import cn.cordys.crm.customer.dto.response.PoolCustomerImportCheckResponse;
import cn.cordys.crm.customer.service.CustomerPoolService;
import cn.cordys.crm.customer.service.PoolCustomerImportService;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.dataspecialist.dto.DataSpecialistTenantOption;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.dataspecialist.support.DataSpecialistAccess;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "数据专员-公海导入")
@RestController
@RequestMapping("/data-specialist")
public class DataSpecialistPortalController {

    @Resource
    private DataSpecialistAccess dataSpecialistAccess;
    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;
    @Resource
    private CustomerPoolService customerPoolService;
    @Resource
    private PoolCustomerImportService poolCustomerImportService;

    @GetMapping("/tenants")
    @Operation(summary = "可导入租户列表")
    public List<DataSpecialistTenantOption> listTenants() {
        dataSpecialistAccess.requireDataSpecialist();
        return extDataSpecialistMapper.listAllowedTenants(SessionUtils.getUserId());
    }

    @GetMapping("/pools")
    @Operation(summary = "当前租户下可用公海池（须带 X-Tenant-ID）")
    public List<CustomerPoolDTO> listPools() {
        dataSpecialistAccess.requireDataSpecialist();
        dataSpecialistAccess.assertCurrentTenantAllowedForSpecialist();
        return customerPoolService.listByEnable(OrganizationContext.getOrganizationId());
    }

    @GetMapping("/pool/import/template/download")
    @Operation(summary = "下载公海导入模板")
    public void downloadImportTpl(HttpServletResponse response) {
        dataSpecialistAccess.requireDataSpecialist();
        dataSpecialistAccess.assertCurrentTenantAllowedForSpecialist();
        poolCustomerImportService.downloadImportTpl(response, OrganizationContext.getOrganizationId());
    }

    @PostMapping("/pool/import/pre-check")
    @Operation(summary = "公海导入预检查")
    public PoolCustomerImportCheckResponse preCheck(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("poolId") String poolId) {
        dataSpecialistAccess.requireDataSpecialist();
        dataSpecialistAccess.assertCurrentTenantAllowedForSpecialist();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = DataSpecialistConstants.specialistUserId(SessionUtils.getUserId());
        return poolCustomerImportService.preCheck(file, poolId, orgId, userId);
    }

    @PostMapping("/pool/import")
    @Operation(summary = "公海导入")
    public String realImport(@RequestParam("file") MultipartFile file,
                             @RequestParam("poolId") String poolId) {
        dataSpecialistAccess.requireDataSpecialist();
        dataSpecialistAccess.assertCurrentTenantAllowedForSpecialist();
        String orgId = OrganizationContext.getOrganizationId();
        String userId = DataSpecialistConstants.specialistUserId(SessionUtils.getUserId());
        return poolCustomerImportService.realImport(file, poolId, userId, orgId);
    }

    @GetMapping("/pool/import/error-file/{fileId}")
    @Operation(summary = "下载公海导入错误文件")
    public void downloadErrorFile(@PathVariable("fileId") String fileId, HttpServletResponse response) {
        dataSpecialistAccess.requireDataSpecialist();
        dataSpecialistAccess.assertCurrentTenantAllowedForSpecialist();
        poolCustomerImportService.downloadErrorFile(fileId, OrganizationContext.getOrganizationId(), response);
    }
}
