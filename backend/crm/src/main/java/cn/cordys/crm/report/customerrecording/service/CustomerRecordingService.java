package cn.cordys.crm.report.customerrecording.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.BaseTreeNode;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.report.customerrecording.dto.request.CustomerRecordingPageRequest;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingEmployeeOptionResponse;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingListResponse;
import cn.cordys.crm.report.customerrecording.mapper.CustomerRecordingMapper;
import cn.cordys.crm.system.service.DepartmentService;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.service.MmbaFacadeService;
import cn.cordys.mybatis.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CustomerRecordingService {

    private static final String RECORDING_TRANSCODE_FORMAT = "mp3";

    @Resource
    private CustomerRecordingMapper customerRecordingMapper;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private DepartmentService departmentService;
    @Resource
    private MmbaFacadeService mmbaFacadeService;
    @Resource
    private BaseMapper<MmbaCallRecordAudit> mmbaCallRecordAuditBaseMapper;

    public Pager<List<CustomerRecordingListResponse>> page(CustomerRecordingPageRequest request,
                                                            String userId,
                                                            String orgId) {
        validateRequest(request);
        VisibleEmployeeScope scope = resolveVisibleEmployeeScope(userId, orgId, request.getDepartmentId());
        List<String> operatorUserIds = resolveOperatorUserIds(scope, request.getEmployeeId());
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        if (operatorUserIds != null && operatorUserIds.isEmpty()) {
            return PageUtils.setPageInfo(page, List.of());
        }
        List<CustomerRecordingListResponse> list = customerRecordingMapper.page(request, operatorUserIds);
        return PageUtils.setPageInfo(page, list);
    }

    public List<CustomerRecordingEmployeeOptionResponse> listEmployeeOptions(String departmentId,
                                                                              String userId,
                                                                              String orgId) {
        return resolveVisibleEmployeeScope(userId, orgId, departmentId).employees();
    }

    public ResponseEntity<ByteArrayResource> previewAudio(String auditId, String userId, String orgId) {
        if (StringUtils.isBlank(auditId)) {
            throw new GenericException("通话记录不存在");
        }
        MmbaCallRecordAudit audit = mmbaCallRecordAuditBaseMapper.selectByPrimaryKey(auditId);
        if (audit == null) {
            throw new GenericException("通话记录不存在");
        }
        String filePath = extractFirstRecordPath(audit.getRecord());
        if (StringUtils.isBlank(filePath)) {
            throw new GenericException("录音文件不存在");
        }
        byte[] bytes = mmbaFacadeService.fetchAssetBinary(buildFetchAssetRequest(filePath), userId, orgId);
        if (bytes == null || bytes.length == 0) {
            throw new GenericException("录音文件为空");
        }
        String fileName = resolvePreviewFileName(filePath);
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + UriUtils.encode(fileName, StandardCharsets.UTF_8))
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private void validateRequest(CustomerRecordingPageRequest request) {
        if (request.getEndTime() < request.getStartTime()) {
            throw new GenericException("通话时间范围不正确");
        }
        if (request.getMinDuration() != null
                && request.getMaxDuration() != null
                && request.getMaxDuration() < request.getMinDuration()) {
            throw new GenericException("最大通话时长不能小于最小时长");
        }
    }

    private VisibleEmployeeScope resolveVisibleEmployeeScope(String userId, String orgId, String departmentId) {
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(
                userId,
                orgId,
                PermissionConstants.CUSTOMER_MANAGEMENT_READ
        );
        List<CustomerRecordingEmployeeOptionResponse> allEmployees = customerRecordingMapper.listCurrentEmployees();
        List<CustomerRecordingEmployeeOptionResponse> visibleEmployees = filterByPermission(
                allEmployees,
                permission,
                userId
        );
        if (StringUtils.isNotBlank(departmentId) && !visibleEmployees.isEmpty()) {
            List<BaseTreeNode> departmentTree = departmentService.getTree(orgId);
            Set<String> departmentIds = new LinkedHashSet<>(
                    dataScopeService.getDeptIdsWithChild(departmentTree, Set.of(departmentId))
            );
            visibleEmployees = visibleEmployees.stream()
                    .filter(item -> departmentIds.contains(item.getDepartmentId()))
                    .toList();
        }
        boolean queryAllEmployees = Boolean.TRUE.equals(permission.getAll()) && StringUtils.isBlank(departmentId);
        return new VisibleEmployeeScope(visibleEmployees, queryAllEmployees);
    }

    private List<CustomerRecordingEmployeeOptionResponse> filterByPermission(
            List<CustomerRecordingEmployeeOptionResponse> employees,
            DeptDataPermissionDTO permission,
            String userId) {
        if (Boolean.TRUE.equals(permission.getAll())) {
            return employees;
        }
        if (Boolean.TRUE.equals(permission.getSelf())) {
            return employees.stream()
                    .filter(item -> StringUtils.equals(item.getId(), userId))
                    .toList();
        }
        Set<String> deptIds = permission.getDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            return employees.stream()
                    .filter(item -> StringUtils.equals(item.getId(), userId))
                    .toList();
        }
        return employees.stream()
                .filter(item -> StringUtils.equals(item.getId(), userId) || deptIds.contains(item.getDepartmentId()))
                .toList();
    }

    private List<String> resolveOperatorUserIds(VisibleEmployeeScope scope, String employeeId) {
        if (StringUtils.isNotBlank(employeeId)) {
            boolean employeeVisible = scope.employees().stream()
                    .anyMatch(item -> StringUtils.equals(item.getId(), employeeId));
            return employeeVisible ? List.of(employeeId) : List.of();
        }
        if (scope.queryAllEmployees()) {
            return null;
        }
        return scope.employees().stream()
                .map(CustomerRecordingEmployeeOptionResponse::getId)
                .toList();
    }

    private JsonNode buildFetchAssetRequest(String filePath) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("filePath", filePath);
        request.put("format", RECORDING_TRANSCODE_FORMAT);
        return request;
    }

    private String extractFirstRecordPath(String rawRecord) {
        if (StringUtils.isBlank(rawRecord)) {
            return null;
        }
        String recordText = StringUtils.trim(rawRecord);
        if (!recordText.startsWith("[")) {
            return StringUtils.trimToNull(recordText);
        }
        try {
            JsonNode recordNode = JSON.parseObject(recordText, JsonNode.class);
            if (!recordNode.isArray() || recordNode.isEmpty()) {
                return null;
            }
            for (JsonNode item : recordNode) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String filePath = StringUtils.trimToNull(item.asText());
                if (StringUtils.isNotBlank(filePath)) {
                    return filePath;
                }
            }
            return null;
        } catch (Exception e) {
            throw new GenericException("录音路径解析失败");
        }
    }

    private String resolvePreviewFileName(String filePath) {
        String normalizedPath = StringUtils.trimToEmpty(filePath);
        int slashIndex = Math.max(normalizedPath.lastIndexOf('/'), normalizedPath.lastIndexOf('\\'));
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        if (StringUtils.isBlank(fileName)) {
            return "customer-recording." + RECORDING_TRANSCODE_FORMAT;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex) + "." + RECORDING_TRANSCODE_FORMAT;
        }
        return fileName + "." + RECORDING_TRANSCODE_FORMAT;
    }

    private record VisibleEmployeeScope(List<CustomerRecordingEmployeeOptionResponse> employees,
                                        boolean queryAllEmployees) {
    }
}
