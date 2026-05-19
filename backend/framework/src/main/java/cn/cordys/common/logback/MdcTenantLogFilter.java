package cn.cordys.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import cn.cordys.common.constants.MdcConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 租户侧路由：MDC 含 tenantId 时返回 {@link FilterReply#NEUTRAL}，交由后续 {@code LevelFilter} 按级别落盘；
 * 返回 {@link FilterReply#ACCEPT} 会短路过滤器链，导致 info/warn/error 写入相同内容。
 */
public class MdcTenantLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String tenantId = event.getMDCPropertyMap().get(MdcConstants.TENANT_ID_KEY);
        if (StringUtils.isNotBlank(tenantId)) {
            return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
    }
}
