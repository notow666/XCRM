package cn.cordys.mmba.callback.consumer;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.callback.AbstractZZYConsumer;
import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import cn.cordys.mmba.service.MmbaCallbackDispatchService;
import cn.cordys.mmba.service.MmbaCallbackProcessService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
@Service
public class AuditCallConsumer extends AbstractZZYConsumer {

    @Resource
    private MmbaCallbackProcessService mmbaCallbackProcessService;
    @Resource
    private MmbaCallbackDispatchService mmbaCallbackDispatchService;

    @Override
    public String group() {
        return MmbaConstants.GROUP_BY_AUDIT;
    }

    @Override
    public void process(MmbaAuditRequest dto) {
        log.info("MMBA审计回调开始消费 streamId={} consumer={} behaviorType={} tenancyName={} count={}",
                dto.getStreamId(), dto.getStreamConsumer(), dto.getBehaviorType(), dto.getTenancyName(),
                dto.getData() == null ? 0 : dto.getData().size());
        log.debug("AuditCallConsumer payload={}", dto.getRawPayload());
        for (Map.Entry<String, List<ZzyData>> entry : groupByTenant(dto).entrySet()) {
            String tenantId = entry.getKey();
            MmbaCallbackProcessService.CallbackRecordPrepareResult prepareResult = null;
            MmbaAuditRequest tenantDto = dto.copyWithData(entry.getValue());
            try {
                TenantContext.setTenantId(tenantId);
                RoutingContext.setPurpose(RoutingPurpose.INTEGRATION_PRIMARY);
                prepareResult = mmbaCallbackProcessService.prepareCallbackRecord(tenantDto);
                if (!prepareResult.shouldProcess()) {
                    continue;
                }
                MmbaCallbackRecord callbackRecord = prepareResult.getCallbackRecord();
                log.info("MMBA审计回调处理租户 streamId={} callbackRecordId={} behaviorType={} tenantId={} recordCount={} reqId={} esId={}",
                        tenantDto.getStreamId(), callbackRecord.getId(), dto.getBehaviorType(), tenantId,
                        entry.getValue().size(), firstReqId(entry.getValue()), firstEsId(entry.getValue()));
                mmbaCallbackDispatchService.dispatchAudit(tenantDto, callbackRecord);
                mmbaCallbackProcessService.markSuccess(callbackRecord, tenantDto);
            } catch (Exception e) {
                log.error("审计回调数据处理异常 streamId={} tenantId={} callbackRecordId={} reqId={} esId={} message={}",
                        tenantDto.getStreamId(), TenantContext.getTenantId(),
                        prepareResult != null && prepareResult.getCallbackRecord() != null
                                ? prepareResult.getCallbackRecord().getId() : null,
                        firstReqId(entry.getValue()), firstEsId(entry.getValue()), e.getMessage(), e);
                if (prepareResult != null && prepareResult.shouldProcess()) {
                    mmbaCallbackProcessService.markFailed(prepareResult.getCallbackRecord(), tenantDto, e);
                }
                throw e;
            } finally {
                RoutingContext.clear();
                TenantContext.clear();
            }
        }
    }

    private Map<String, List<ZzyData>> groupByTenant(MmbaAuditRequest dto) {
        Map<String, List<ZzyData>> tenantDataMap = new LinkedHashMap<>();
        if (dto.getData() == null) {
            return tenantDataMap;
        }
        for (ZzyData zzy : dto.getData()) {
            tenantDataMap.computeIfAbsent(zzy.getTenantId(), key -> new ArrayList<>()).add(zzy);
        }
        return tenantDataMap;
    }

    private String firstReqId(List<ZzyData> dataList) {
        return dataList == null || dataList.isEmpty() ? null : dataList.get(0).getReqId();
    }

    private String firstEsId(List<ZzyData> dataList) {
        return dataList == null || dataList.isEmpty() ? null : dataList.get(0).getEsId();
    }
}
