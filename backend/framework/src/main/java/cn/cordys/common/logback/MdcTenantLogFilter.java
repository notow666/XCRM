package cn.cordys.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import cn.cordys.common.constants.MdcConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 仅放行 MDC 中带有非空 {@link MdcConstants#TENANT_ID_KEY} 的日志事件（租户侧）。
 */
public class MdcTenantLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String tenantId = event.getMDCPropertyMap().get(MdcConstants.TENANT_ID_KEY);
        if (StringUtils.isNotBlank(tenantId)) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }
}
