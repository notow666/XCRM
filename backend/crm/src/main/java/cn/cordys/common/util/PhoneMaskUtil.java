package cn.cordys.common.util;

import org.apache.commons.lang3.StringUtils;

public final class PhoneMaskUtil {

    private static final int MASK_LENGTH = 6;
    private static final String MASK = "******";

    private PhoneMaskUtil() {
    }

    public static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return phone;
        }
        int length = phone.length();
        if (length > MASK_LENGTH) {
            return phone.substring(0, length - MASK_LENGTH) + MASK;
        }
        return MASK;
    }
}
