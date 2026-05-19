package cn.cordys.mmba.callback;

import cn.cordys.common.constants.MdcConstants;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public abstract class AbstractZZYConsumer implements ZZYConsumerService{

    public final void mainProcess(MmbaAuditRequest dto) {
        process(dto);
    }

    protected abstract void process(MmbaAuditRequest dto);

    protected void resetMdc(String traceId, String tenantId) {
        MDC.put(MdcConstants.TRACE_ID_KEY, traceId);
        MDC.put(MdcConstants.TENANT_ID_KEY, tenantId);
    }

    protected void clearMdc() {
        MDC.remove(MdcConstants.TRACE_ID_KEY);
        MDC.remove(MdcConstants.TENANT_ID_KEY);
    }
}
