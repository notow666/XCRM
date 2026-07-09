package cn.cordys.context;

import org.apache.commons.lang3.StringUtils;

/**
 * A→B 切换维护窗状态。
 */
public enum ShadowMaintenanceState {

    /** 30s 预告阶段，尚未拦截 UI */
    PRE_NOTICE,

    /** 拦截 USER_REQUEST 阶段 */
    BLOCKING;

    public static ShadowMaintenanceState fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
