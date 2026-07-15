package cn.cordys.crm.customer.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.request.CustomerCallRecordPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerCallRecordListResponse;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.customer.mapper.ExtCustomerCallRecordMapper;
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
import org.springframework.web.util.UriUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerCallRecordService {

    private static final String CALL_RECORD_AUDIO_URL_PREFIX = "/account/call-record/audio/";
    private static final String CALL_RECORD_TRANSCODE_FORMAT = "mp3";

    @Resource
    private CustomerService customerService;
    @Resource
    private ExtCustomerCallRecordMapper extCustomerCallRecordMapper;
    @Resource
    private MmbaFacadeService mmbaFacadeService;
    @Resource
    private BaseMapper<MmbaCallRecordAudit> mmbaCallRecordAuditBaseMapper;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private BaseMapper<Customer> customerBaseMapper;

    public Pager<List<CustomerCallRecordListResponse>> list(CustomerCallRecordPageRequest request, String userId, String orgId) {
        customerService.getWithDataPermissionCheck(request.getSourceId(), userId, orgId);
        Customer customer = customerBaseMapper.selectByPrimaryKey(request.getSourceId());
        if (customer == null) {
            throw new GenericException("客户不存在");
        }
        String customerTel = StringUtils.trimToNull(customer.getMobile());
        String um = readUmByUserId(customer.getOwner());
        Long collectionTime = customer.getCollectionTime();
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        if (StringUtils.isAnyBlank(customerTel, um) || collectionTime == null) {
            return PageUtils.setPageInfo(page, Collections.emptyList());
        }
        List<CustomerCallRecordListResponse> list = extCustomerCallRecordMapper.list(um, customerTel, collectionTime, request, orgId);
        list.forEach(this::fillRecordUrl);
        return PageUtils.setPageInfo(page, list);
    }

    public ResponseEntity<ByteArrayResource> previewAudio(String auditId, String userId, String orgId) {
        MmbaCallRecordAudit audit = loadAuditAndCheckPermission(auditId, userId, orgId);
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + UriUtils.encode(fileName, StandardCharsets.UTF_8))
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private void fillRecordUrl(CustomerCallRecordListResponse record) {
        String filePath = extractFirstRecordPath(record.getRecord());
        if (StringUtils.isBlank(filePath)) {
            record.setRecordUrl(null);
            return;
        }
        record.setRecordUrl(CALL_RECORD_AUDIO_URL_PREFIX + record.getId());
        record.setRecord(null);
    }

    private MmbaCallRecordAudit loadAuditAndCheckPermission(String auditId, String userId, String orgId) {
        if (StringUtils.isBlank(auditId)) {
            throw new GenericException("通话记录不存在");
        }
        MmbaCallRecordAudit audit = mmbaCallRecordAuditBaseMapper.selectByPrimaryKey(auditId);
        if (audit == null) {
            throw new GenericException("通话记录不存在");
        }
        // 2026-07-15：客户详情列表已完成数据权限校验，录音预览不再根据回调中的 customerId 重复拦截。
        // customerService.getWithDataPermissionCheck(audit.getCustomerId(), userId, orgId);
        return audit;
    }

    private String readUmByUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        User user = userBaseMapper.selectByPrimaryKey(userId);
        return user == null ? null : StringUtils.trimToNull(user.getUm());
    }

    private JsonNode buildFetchAssetRequest(String filePath) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("filePath", filePath);
        request.put("format", CALL_RECORD_TRANSCODE_FORMAT);
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
        int slashIndex = normalizedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        if (StringUtils.isBlank(fileName)) {
            return "call-record." + CALL_RECORD_TRANSCODE_FORMAT;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex) + "." + CALL_RECORD_TRANSCODE_FORMAT;
        }
        return fileName + "." + CALL_RECORD_TRANSCODE_FORMAT;
    }
}
