package cn.cordys.mmba.callback.consumer;

import cn.cordys.common.util.JSON;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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
        log.info("MMBA审计回调开始消费 behaviorType={} tenancyName={} contextTenantId={} count={}",
                dto.getBehaviorType(), dto.getTenancyName(), TenantContext.getTenantId(), dto.getData() == null ? 0 : dto.getData().size());
        log.debug("AuditCallConsumer payload={}", dto.getRawPayload());
        for (Map.Entry<String, List<ZzyData>> entry : groupByTenant(dto).entrySet()) {
            String previousTenantId = TenantContext.getTenantId();
            String tenantId = entry.getKey();
            MmbaCallbackProcessService.CallbackRecordPrepareResult prepareResult = null;
            MmbaAuditRequest tenantDto = dto.copyWithData(entry.getValue());
            try {
                TenantContext.setTenantId(tenantId);
                prepareResult = mmbaCallbackProcessService.prepareCallbackRecord(tenantDto);
                if (!prepareResult.shouldProcess()) {
                    continue;
                }
                MmbaCallbackRecord callbackRecord = prepareResult.getCallbackRecord();
                log.info("MMBA审计回调切换租户 callbackRecordId={} behaviorType={} tenantId={} recordCount={}",
                        callbackRecord.getId(), dto.getBehaviorType(), tenantId, entry.getValue().size());
                mmbaCallbackDispatchService.dispatchAudit(tenantDto, callbackRecord);
                mmbaCallbackProcessService.markSuccess(callbackRecord);
            } catch (Exception e) {
                log.error("审计回调数据处理异常 contextTenantId={} message={} dto={}",
                        TenantContext.getTenantId(), e.getMessage(), JSON.toJSONString(tenantDto), e);
                if (prepareResult != null && prepareResult.shouldProcess()) {
                    mmbaCallbackProcessService.markFailed(prepareResult.getCallbackRecord(), e);
                }
                throw e;
            } finally {
                if (StringUtils.isBlank(previousTenantId)) {
                    TenantContext.clear();
                } else {
                    TenantContext.setTenantId(previousTenantId);
                }
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
}
