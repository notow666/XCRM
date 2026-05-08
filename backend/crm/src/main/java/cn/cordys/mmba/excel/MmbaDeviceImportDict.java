package cn.cordys.mmba.excel;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 导入 Excel 中设备状态、在线状态字段解析（支持数字或中文文案）。
 */
public final class MmbaDeviceImportDict {

    private static final Set<Integer> DEVICE_STATUS_CODES = Set.of(1, 3, 4, 5, 6, 7, 8, 9);

    private static final Map<String, Integer> DEVICE_STATUS_LABEL_ZH = Map.ofEntries(
            Map.entry("正常", 1),
            Map.entry("预注册", 3),
            Map.entry("已丢失", 4),
            Map.entry("待删除", 5),
            Map.entry("闲置", 6),
            Map.entry("已擦除", 7),
            Map.entry("待擦除", 8),
            Map.entry("已删除", 9));

    private static final Map<String, Integer> DEVICE_STATUS_LABEL_EN = Map.ofEntries(
            Map.entry("normal", 1),
            Map.entry("pre-registered", 3),
            Map.entry("pre registered", 3),
            Map.entry("lost", 4),
            Map.entry("pending deletion", 5),
            Map.entry("idle", 6),
            Map.entry("erased", 7),
            Map.entry("pending erase", 8),
            Map.entry("deleted", 9));

    private static final Map<String, Integer> LOGIN_LABEL_ZH = Map.of(
            "在线", 1,
            "离线", 0);

    private static final Map<String, Integer> LOGIN_LABEL_EN = Map.of(
            "online", 1,
            "offline", 0);

    private MmbaDeviceImportDict() {
    }

    public static Integer parseDeviceStatus(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        String normalized = normalizeExcelNumber(trimmed);
        Integer asInt = tryParseInt(normalized);
        if (asInt != null) {
            if (DEVICE_STATUS_CODES.contains(asInt)) {
                return asInt;
            }
            throw new GenericException(Translator.getWithArgs("mmba_device_import_invalid_device_status", raw));
        }
        Integer fromZh = DEVICE_STATUS_LABEL_ZH.get(trimmed);
        if (fromZh != null) {
            return fromZh;
        }
        Integer fromEn = DEVICE_STATUS_LABEL_EN.get(trimmed.toLowerCase(Locale.ROOT));
        if (fromEn != null) {
            return fromEn;
        }
        throw new GenericException(Translator.getWithArgs("mmba_device_import_invalid_device_status", raw));
    }

    public static String parseDeviceStatus(Integer raw) {
        if (!DEVICE_STATUS_CODES.contains(raw)) {
            return "";
        }
        for (Map.Entry<String, Integer> entry : DEVICE_STATUS_LABEL_ZH.entrySet()) {
            String k = entry.getKey();
            Integer v = entry.getValue();
            if (v.equals(raw)) {
                return k;
            }
        }
        throw new GenericException(Translator.getWithArgs("mmba_device_import_invalid_device_status", raw));
    }

    public static Integer parseLoginStatus(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        String normalized = normalizeExcelNumber(trimmed);
        Integer asInt = tryParseInt(normalized);
        if (asInt != null) {
            if (asInt == 0 || asInt == 1) {
                return asInt;
            }
            throw new GenericException(Translator.getWithArgs("mmba_device_import_invalid_login_status", raw));
        }
        Integer fromZh = LOGIN_LABEL_ZH.get(trimmed);
        if (fromZh != null) {
            return fromZh;
        }
        Integer fromEn = LOGIN_LABEL_EN.get(trimmed.toLowerCase(Locale.ROOT));
        if (fromEn != null) {
            return fromEn;
        }
        throw new GenericException(Translator.getWithArgs("mmba_device_import_invalid_login_status", raw));
    }

    private static String normalizeExcelNumber(String s) {
        if (s.matches("\\d+\\.0+")) {
            return s.substring(0, s.indexOf('.'));
        }
        return s;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
