package cn.cordys.context;

import org.apache.commons.lang3.StringUtils;

/**
 * Master 元数据中租户当前 UI 指向的业务库角色。
 */
public enum ActiveDbRole {

    PRIMARY,
    SHADOW;

    public static ActiveDbRole fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return PRIMARY;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PRIMARY;
        }
    }
}
