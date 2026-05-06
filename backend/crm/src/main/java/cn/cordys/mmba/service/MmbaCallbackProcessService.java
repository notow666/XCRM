package cn.cordys.mmba.service;

import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaCallbackProcessService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 60000;

    @Resource
    private MmbaCallbackRecordService mmbaCallbackRecordService;

    public CallbackRecordPrepareResult prepareCallbackRecord(MmbaAuditRequest dto) {
        MmbaCallbackRecord record = buildCallbackRecord(dto);
        MmbaCallbackRecord existing = mmbaCallbackRecordService.findLatestByPayloadHash(record.getPayloadHash());
        if (existing == null) {
            log.info("MMBA回调入库初始化 behaviorType={} tenancyName={} tenantId={} recordCount={} payloadHash={}",
                    dto.getBehaviorType(), dto.getTenancyName(), TenantContext.getTenantId(),
                    record.getRecordCount(), record.getPayloadHash());
            return new CallbackRecordPrepareResult(mmbaCallbackRecordService.init(record, MmbaConstants.SYSTEM_USER), true);
        }
        if (MmbaConstants.CALLBACK_PROCESS_SUCCESS.equals(existing.getProcessStatus())) {
            log.info("MMBA回调命中成功去重，跳过重复处理 behaviorType={} tenantId={} callbackRecordId={} payloadHash={}",
                    dto.getBehaviorType(), TenantContext.getTenantId(), existing.getId(), existing.getPayloadHash());
            return new CallbackRecordPrepareResult(existing, false);
        }
        existing.setBehaviorType(record.getBehaviorType());
        existing.setTenancyName(record.getTenancyName());
        existing.setRecordCount(record.getRecordCount());
        existing.setPayloadRaw(record.getPayloadRaw());
        existing.setPayloadHash(record.getPayloadHash());
        existing.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_PENDING);
        existing.setProcessResult(null);
        existing.setErrorMessage(null);
        existing.setRetryCount((existing.getRetryCount() == null ? 0 : existing.getRetryCount()) + 1);
        mmbaCallbackRecordService.update(existing, MmbaConstants.SYSTEM_USER);
        log.info("MMBA回调命中待处理/失败记录，重新处理 behaviorType={} tenantId={} callbackRecordId={} payloadHash={} retryCount={}",
                dto.getBehaviorType(), TenantContext.getTenantId(), existing.getId(), existing.getPayloadHash(), existing.getRetryCount());
        return new CallbackRecordPrepareResult(existing, true);
    }

    public MmbaCallbackRecord initCallbackRecord(MmbaAuditRequest dto) {
        MmbaCallbackRecord record = buildCallbackRecord(dto);
        log.info("MMBA回调入库初始化 behaviorType={} tenancyName={} tenantId={} recordCount={} payloadHash={}",
                dto.getBehaviorType(), dto.getTenancyName(), TenantContext.getTenantId(),
                record.getRecordCount(), record.getPayloadHash());
        return mmbaCallbackRecordService.init(record, MmbaConstants.SYSTEM_USER);
    }

    public void markSuccess(MmbaCallbackRecord record) {
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_SUCCESS);
        record.setProcessResult("processed");
        record.setErrorMessage(null);
        mmbaCallbackRecordService.update(record, MmbaConstants.SYSTEM_USER);
        log.info("MMBA回调处理成功 callbackRecordId={} behaviorType={} tenantId={}",
                record.getId(), record.getBehaviorType(), TenantContext.getTenantId());
    }

    public void markFailed(MmbaCallbackRecord record, Exception exception) {
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_FAILED);
        record.setProcessResult("failed");
        record.setErrorMessage(truncateErrorMessage(exception == null ? null : exception.getMessage()));
        mmbaCallbackRecordService.update(record, MmbaConstants.SYSTEM_USER);
        log.error("MMBA回调处理失败 callbackRecordId={} behaviorType={} tenantId={} message={}",
                record.getId(), record.getBehaviorType(), TenantContext.getTenantId(),
                exception == null ? null : exception.getMessage(), exception);
    }

    public void touchPayloadError(MmbaAuditRequest dto, Exception exception) {
        MmbaCallbackRecord record = initCallbackRecord(dto);
        markFailed(record, exception);
    }

    public String toRawData(ZzyData data) {
        return JSON.toJSONString(data);
    }

    private MmbaCallbackRecord buildCallbackRecord(MmbaAuditRequest dto) {
        MmbaCallbackRecord record = new MmbaCallbackRecord();
        record.setBehaviorType(dto.getBehaviorType());
        record.setTenancyName(dto.getTenancyName());
        record.setRecordCount(CollectionUtils.isEmpty(dto.getData()) ? 0 : dto.getData().size());
        record.setPayloadRaw(JSON.toJSONString(dto));
        record.setPayloadHash(DigestUtils.md5DigestAsHex(record.getPayloadRaw().getBytes(StandardCharsets.UTF_8)));
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_PENDING);
        record.setRetryCount(0);
        return record;
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage) || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    public static final class CallbackRecordPrepareResult {
        private final MmbaCallbackRecord callbackRecord;
        private final boolean shouldProcess;

        public CallbackRecordPrepareResult(MmbaCallbackRecord callbackRecord, boolean shouldProcess) {
            this.callbackRecord = callbackRecord;
            this.shouldProcess = shouldProcess;
        }

        public MmbaCallbackRecord getCallbackRecord() {
            return callbackRecord;
        }

        public boolean shouldProcess() {
            return shouldProcess;
        }
    }
}
