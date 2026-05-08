package cn.cordys.mmba.util;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * 将「最后上线时间」字符串解析为毫秒时间戳，与 {@code lastOnline} 字段一致。
 */
public final class MmbaLastOnlineParser {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("uuuu-M-d H:m:s", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
    };

    private static final DateTimeFormatter[] DATE_ONLY_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.CHINA).withResolverStyle(ResolverStyle.SMART),
    };

    private MmbaLastOnlineParser() {
    }

    /**
     * 支持：13/10 位毫秒或秒时间戳、常见日期时间字符串；无法解析时返回 {@code null}。
     */
    public static Long toEpochMillisOrNull(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("\\d{13}(\\.0+)?")) {
            int dot = s.indexOf('.');
            return Long.parseLong(dot > 0 ? s.substring(0, dot) : s);
        }
        if (s.matches("\\d{10}(\\.0+)?")) {
            int dot = s.indexOf('.');
            long sec = Long.parseLong(dot > 0 ? s.substring(0, dot) : s);
            return sec * 1000L;
        }
        for (DateTimeFormatter f : DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(s, f);
                return ldt.atZone(ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        for (DateTimeFormatter f : DATE_ONLY_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(s, f);
                return ld.atStartOfDay(ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }
}
