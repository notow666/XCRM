package cn.cordys.crm.tools.constants;

import cn.cordys.common.util.PhoneMaskUtil;
import org.apache.commons.lang3.StringUtils;

public final class NumberCubeMaskMode {

    public static final String PLAIN = "PLAIN";
    public static final String MASKED = "MASKED";

    private NumberCubeMaskMode() {
    }

    public static String normalize(String maskMode) {
        if (StringUtils.equalsIgnoreCase(maskMode, MASKED)) {
            return MASKED;
        }
        return PLAIN;
    }

    public static String maskPhone(String phone) {
        return PhoneMaskUtil.maskGlobalPhone(phone);
    }
}
