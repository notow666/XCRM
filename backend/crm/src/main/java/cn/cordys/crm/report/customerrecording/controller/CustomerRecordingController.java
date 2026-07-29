package cn.cordys.crm.report.customerrecording.controller;

import cn.cordys.common.pager.Pager;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.report.customerrecording.dto.request.CustomerRecordingPageRequest;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingEmployeeOptionResponse;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingListResponse;
import cn.cordys.crm.report.customerrecording.service.CustomerRecordingService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "客户录音")
@Validated
@RestController
@RequestMapping("/report/customer-recording")
public class CustomerRecordingController {

    @Resource
    private CustomerRecordingService customerRecordingService;

    @PostMapping("/page")
    @Operation(summary = "客户录音分页列表")
    public Pager<List<CustomerRecordingListResponse>> page(@Valid @RequestBody CustomerRecordingPageRequest request) {
        return customerRecordingService.page(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/employee/options")
    @Operation(summary = "客户录音员工选项")
    public List<CustomerRecordingEmployeeOptionResponse> listEmployeeOptions(
            @RequestParam(value = "departmentId", required = false) String departmentId) {
        return customerRecordingService.listEmployeeOptions(
                departmentId,
                SessionUtils.getUserId(),
                OrganizationContext.getOrganizationId()
        );
    }

    @GetMapping("/audio/{auditId}")
    @Operation(summary = "客户录音预览")
    public ResponseEntity<ByteArrayResource> previewAudio(@PathVariable("auditId") String auditId) {
        return customerRecordingService.previewAudio(
                auditId,
                SessionUtils.getUserId(),
                OrganizationContext.getOrganizationId()
        );
    }
}
