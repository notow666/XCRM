package cn.cordys.common.constants;

import org.apache.commons.lang3.StringUtils;

/**
 * SSE 订阅主体类型：与租户内用户、平台管理员、数据专员的登录身份对应，连接键与租户切换解耦。
 */
public enum SsePrincipalKind {

    TENANT,
    PLATFORM,
    DATA_SPECIALIST;

    public static SsePrincipalKind fromQuery(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TENANT;
        }
        try {
            return SsePrincipalKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TENANT;
        }
    }
}
