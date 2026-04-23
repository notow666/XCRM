package cn.cordys.common.util;

import org.apache.commons.lang3.StringUtils;

public final class PhoneMaskUtil {

    private static final int MASK_LENGTH = 6;
    private static final String MASK = "******";
    private static final int GLOBAL_PHONE_PREFIX_LENGTH = 3;
    private static final int GLOBAL_PHONE_SUFFIX_LENGTH = 4;
    private static final String GLOBAL_PHONE_MASK = "****";

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

    public static String maskGlobalPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return phone;
        }
        int length = phone.length();
        if (length <= GLOBAL_PHONE_SUFFIX_LENGTH) {
            return phone;
        }
        if (length <= GLOBAL_PHONE_PREFIX_LENGTH + GLOBAL_PHONE_SUFFIX_LENGTH) {
            return GLOBAL_PHONE_MASK + phone.substring(length - GLOBAL_PHONE_SUFFIX_LENGTH);
        }
        return phone.substring(0, GLOBAL_PHONE_PREFIX_LENGTH)
                + GLOBAL_PHONE_MASK
                + phone.substring(length - GLOBAL_PHONE_SUFFIX_LENGTH);
    }
}
