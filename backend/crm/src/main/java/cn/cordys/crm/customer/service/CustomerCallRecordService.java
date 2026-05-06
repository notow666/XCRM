package cn.cordys.crm.customer.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.crm.customer.dto.request.CustomerCallRecordPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerCallRecordListResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerCallRecordMapper;
import cn.cordys.mmba.MmbaBehaviorTypes;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaMediaFile;
import cn.cordys.mmba.service.MmbaMediaFileService;
import cn.cordys.mybatis.BaseMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerCallRecordService {

    @Resource
    private CustomerService customerService;
    @Resource
    private ExtCustomerCallRecordMapper extCustomerCallRecordMapper;
    @Resource
    private MmbaMediaFileService mmbaMediaFileService;
    @Resource
    private BaseMapper<MmbaCallRecordAudit> mmbaCallRecordAuditMapper;

    public Pager<List<CustomerCallRecordListResponse>> list(CustomerCallRecordPageRequest request, String userId, String orgId) {
        customerService.getWithDataPermissionCheck(request.getSourceId(), userId, orgId);
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<CustomerCallRecordListResponse> list = extCustomerCallRecordMapper.list(request, orgId);
        return PageUtils.setPageInfo(page, list);
    }

    public ResponseEntity<org.springframework.core.io.Resource> previewAudio(String mediaFileId, String userId, String orgId) {
        MmbaMediaFile mediaFile = mmbaMediaFileService.findById(mediaFileId);
        if (mediaFile == null || !Objects.equals(mediaFile.getBehaviorType(), MmbaBehaviorTypes.CALL_RECORD_AUDIT)) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        MmbaCallRecordAudit audit = loadAndCheckAudit(mediaFile.getAuditRecordId());
        customerService.getWithDataPermissionCheck(audit.getCustomerId(), userId, orgId);

        if (StringUtils.isBlank(mediaFile.getStoragePath())) {
            throw new GenericException("录音文件不存在");
        }
        Path filePath = Paths.get(mediaFile.getStoragePath());
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new GenericException("录音文件不存在");
        }

        try {
            InputStream inputStream = Files.newInputStream(filePath);
            long fileSize = Files.size(filePath);
            String contentType = Files.probeContentType(filePath);
            if (StringUtils.isBlank(contentType)) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            String fileName = StringUtils.defaultIfBlank(mediaFile.getFileName(), filePath.getFileName().toString());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeName(fileName))
                    .contentLength(fileSize)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            throw new GenericException("读取录音文件失败");
        }
    }

    private MmbaCallRecordAudit loadAndCheckAudit(String auditRecordId) {
        if (StringUtils.isBlank(auditRecordId)) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        MmbaCallRecordAudit audit = mmbaCallRecordAuditMapper.selectByPrimaryKey(auditRecordId);
        if (audit == null || StringUtils.isBlank(audit.getCustomerId())) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        return audit;
    }

    private String encodeName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }
}
