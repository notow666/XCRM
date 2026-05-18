package cn.cordys.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.constants.MdcConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 放行无租户 MDC 且非 MMBA 回调专用 Logger 的日志（平台/数据专员/定时任务等）。
 */
public class MdcMasterLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (CrmLoggers.MMBA_CALLBACK.equals(event.getLoggerName())) {
            return FilterReply.DENY;
        }
        String tenantId = event.getMDCPropertyMap().get(MdcConstants.TENANT_ID_KEY);
        if (StringUtils.isBlank(tenantId)) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }
}
