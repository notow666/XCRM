package cn.cordys.common.constants;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 专用 SLF4J logger 名称，与 logback-spring.xml 中 {@code <logger name="...">} 对应。
 */
public final class CrmLoggers {

    /** MMBA 平台匿名回调链路（入队、消费、分发），独立写入 logs/mmba/ */
    public static final String MMBA_CALLBACK = "MMBA_CALLBACK_LOG";

    public static Marker MMBA_CALLBACK_MARKER = MarkerFactory.getMarker(CrmLoggers.MMBA_CALLBACK);

    private CrmLoggers() {
    }
}
