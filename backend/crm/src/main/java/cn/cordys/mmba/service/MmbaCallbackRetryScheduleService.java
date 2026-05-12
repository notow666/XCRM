package cn.cordys.mmba.service;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.common.util.JSON;
import cn.cordys.mmba.MmbaBehaviorTypes;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.callback.consumer.AuditCallConsumer;
import cn.cordys.mmba.callback.consumer.CommandCallConsumer;
import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mmba.callback.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MmbaCallbackRetryScheduleService {

    private static final int RETRY_BATCH_LIMIT = 20;
    private static final int MAX_RETRY_COUNT = 3;

    @Resource
    private TenantTaskExecutor tenantTaskExecutor;
    @Resource
    private MmbaCallbackRecordService mmbaCallbackRecordService;
    @Resource
    private AuditCallConsumer auditCallConsumer;
    @Resource
    private CommandCallConsumer commandCallConsumer;

    // 暂停数据库失败回调的定时补偿，仅保留 Redis 侧重试链路。
//    @Scheduled(
//            initialDelayString = "${mmba.callback.retry.initial-delay-ms:120000}",
//            fixedDelayString = "${mmba.callback.retry.fixed-delay-ms:300000}"
//    )
    public void retryFailedCallbacks() {
        tenantTaskExecutor.runForEachEnabledTenant("mmba-callback-retry", tenantId -> {
            List<MmbaCallbackRecord> records = mmbaCallbackRecordService
                    .listRetryableFailedRecords(RETRY_BATCH_LIMIT, MAX_RETRY_COUNT);
            if (records.isEmpty()) {
                return;
            }
            log.info("MMBA失败回调补偿开始 tenantId={} count={}", tenantId, records.size());
            for (MmbaCallbackRecord record : records) {
                retryRecord(record, tenantId);
            }
        });
    }

    private void retryRecord(MmbaCallbackRecord record, String tenantId) {
        if (record == null || !StringUtils.hasText(record.getPayloadRaw())) {
            return;
        }
        try {
            MmbaAuditRequest request = JSON.parseObject(record.getPayloadRaw(), MmbaAuditRequest.class);
            if (request == null) {
                log.warn("MMBA失败回调补偿跳过，payload 无法解析 callbackRecordId={} tenantId={}", record.getId(), tenantId);
                return;
            }
            request.setRawPayload(record.getPayloadRaw());
            request.hydrateDataRawPayload();
            hydrateTenantId(request, tenantId);
            String group = MmbaBehaviorTypes.SUPPORTED.get(request.getBehaviorType());
            if (MmbaConstants.GROUP_BY_AUDIT.equals(group)) {
                auditCallConsumer.process(request);
                return;
            }
            if (MmbaConstants.GROUP_BY_COMMAND.equals(group)) {
                commandCallConsumer.process(request);
                return;
            }
            log.warn("MMBA失败回调补偿跳过，behaviorType 未注册 callbackRecordId={} behaviorType={} tenantId={}",
                    record.getId(), request.getBehaviorType(), tenantId);
        } catch (Exception e) {
            log.error("MMBA失败回调补偿执行失败 callbackRecordId={} behaviorType={} tenantId={}",
                    record.getId(), record.getBehaviorType(), tenantId, e);
        }
    }

    private void hydrateTenantId(MmbaAuditRequest request, String tenantId) {
        if (request == null || !StringUtils.hasText(tenantId) || request.getData() == null) {
            return;
        }
        for (ZzyData item : request.getData()) {
            if (item != null && !StringUtils.hasText(item.getTenantId())) {
                item.setTenantId(tenantId);
            }
        }
    }
}
