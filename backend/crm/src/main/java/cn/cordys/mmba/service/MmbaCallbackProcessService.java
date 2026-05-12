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
            log.info("MMBA回调入库初始化 streamId={} consumer={} behaviorType={} tenancyName={} tenantId={} recordCount={} payloadHash={} reqId={} esId={}",
                    dto.getStreamId(), dto.getStreamConsumer(), dto.getBehaviorType(), dto.getTenancyName(),
                    TenantContext.getTenantId(), record.getRecordCount(), record.getPayloadHash(),
                    firstReqId(dto), firstEsId(dto));
            return new CallbackRecordPrepareResult(mmbaCallbackRecordService.init(record, MmbaConstants.SYSTEM_USER), true);
        }
        if (MmbaConstants.CALLBACK_PROCESS_SUCCESS.equals(existing.getProcessStatus())) {
            log.info("MMBA回调命中成功去重，跳过重复处理 streamId={} consumer={} behaviorType={} tenantId={} callbackRecordId={} payloadHash={} reqId={} esId={}",
                    dto.getStreamId(), dto.getStreamConsumer(), dto.getBehaviorType(), TenantContext.getTenantId(),
                    existing.getId(), existing.getPayloadHash(), firstReqId(dto), firstEsId(dto));
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
        log.info("MMBA回调命中待处理/失败记录，重新处理 streamId={} consumer={} behaviorType={} tenantId={} callbackRecordId={} payloadHash={} retryCount={} reqId={} esId={}",
                dto.getStreamId(), dto.getStreamConsumer(), dto.getBehaviorType(), TenantContext.getTenantId(),
                existing.getId(), existing.getPayloadHash(), existing.getRetryCount(), firstReqId(dto), firstEsId(dto));
        return new CallbackRecordPrepareResult(existing, true);
    }

    public MmbaCallbackRecord initCallbackRecord(MmbaAuditRequest dto) {
        MmbaCallbackRecord record = buildCallbackRecord(dto);
        log.info("MMBA回调入库初始化 streamId={} consumer={} behaviorType={} tenancyName={} tenantId={} recordCount={} payloadHash={} reqId={} esId={}",
                dto.getStreamId(), dto.getStreamConsumer(), dto.getBehaviorType(), dto.getTenancyName(),
                TenantContext.getTenantId(), record.getRecordCount(), record.getPayloadHash(),
                firstReqId(dto), firstEsId(dto));
        return mmbaCallbackRecordService.init(record, MmbaConstants.SYSTEM_USER);
    }

    public void markSuccess(MmbaCallbackRecord record, MmbaAuditRequest dto) {
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_SUCCESS);
        record.setProcessResult("processed");
        record.setErrorMessage(null);
        mmbaCallbackRecordService.update(record, MmbaConstants.SYSTEM_USER);
        log.info("MMBA回调处理成功 streamId={} consumer={} callbackRecordId={} behaviorType={} tenantId={} reqId={} esId={}",
                dto == null ? null : dto.getStreamId(), dto == null ? null : dto.getStreamConsumer(),
                record.getId(), record.getBehaviorType(), TenantContext.getTenantId(),
                firstReqId(dto), firstEsId(dto));
    }

    public void markFailed(MmbaCallbackRecord record, MmbaAuditRequest dto, Exception exception) {
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_FAILED);
        record.setProcessResult("failed");
        record.setErrorMessage(truncateErrorMessage(exception == null ? null : exception.getMessage()));
        mmbaCallbackRecordService.update(record, MmbaConstants.SYSTEM_USER);
        log.error("MMBA回调处理失败 streamId={} consumer={} callbackRecordId={} behaviorType={} tenantId={} reqId={} esId={} message={}",
                dto == null ? null : dto.getStreamId(), dto == null ? null : dto.getStreamConsumer(),
                record.getId(), record.getBehaviorType(), TenantContext.getTenantId(),
                firstReqId(dto), firstEsId(dto),
                exception == null ? null : exception.getMessage(), exception);
    }

    public void touchPayloadError(MmbaAuditRequest dto, Exception exception) {
        MmbaCallbackRecord record = initCallbackRecord(dto);
        markFailed(record, dto, exception);
    }

    public String toRawData(ZzyData data) {
        return JSON.toJSONString(data);
    }

    private MmbaCallbackRecord buildCallbackRecord(MmbaAuditRequest dto) {
        String rawPayload = requireRawPayload(dto);
        MmbaCallbackRecord record = new MmbaCallbackRecord();
        record.setBehaviorType(dto.getBehaviorType());
        record.setTenancyName(dto.getTenancyName());
        record.setRecordCount(CollectionUtils.isEmpty(dto.getData()) ? 0 : dto.getData().size());
        record.setPayloadRaw(rawPayload);
        record.setPayloadHash(DigestUtils.md5DigestAsHex(rawPayload.getBytes(StandardCharsets.UTF_8)));
        record.setProcessStatus(MmbaConstants.CALLBACK_PROCESS_PENDING);
        record.setRetryCount(0);
        return record;
    }

    private String requireRawPayload(MmbaAuditRequest dto) {
        if (dto != null && StringUtils.hasText(dto.getRawPayload())) {
            return dto.getRawPayload();
        }
        throw new IllegalStateException("MMBA回调缺少原始payload，禁止使用DTO重序列化结果入库");
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage) || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String firstReqId(MmbaAuditRequest dto) {
        return dto == null || CollectionUtils.isEmpty(dto.getData()) ? null : dto.getData().get(0).getReqId();
    }

    private String firstEsId(MmbaAuditRequest dto) {
        return dto == null || CollectionUtils.isEmpty(dto.getData()) ? null : dto.getData().get(0).getEsId();
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
