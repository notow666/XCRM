package cn.cordys.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.constants.MdcConstants;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Master 侧路由：无 tenantId 且非 MMBA 专用 Logger 时返回 {@link FilterReply#NEUTRAL}，交由 {@code LevelFilter} 分级别；
 * 不可返回 {@link FilterReply#ACCEPT}，否则会跳过后续级别过滤器。
 */
public class MdcMasterLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (CrmLoggers.MMBA_CALLBACK.equals(event.getLoggerName()) ||
                (CollectionUtils.isNotEmpty(event.getMarkerList()) && event.getMarkerList().contains(CrmLoggers.MMBA_CALLBACK_MARKER))) {
            return FilterReply.DENY;
        }
        String tenantId = event.getMDCPropertyMap().get(MdcConstants.TENANT_ID_KEY);
        if (StringUtils.isBlank(tenantId)) {
            return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
    }
}
