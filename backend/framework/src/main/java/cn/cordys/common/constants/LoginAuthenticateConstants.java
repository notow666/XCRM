package cn.cordys.common.constants;

public class LoginAuthenticateConstants {

    /**
     * Shiro 登录路由类型（写入 Session {@code authenticate}），与业务侧 {@link UserSource}（OAuth 等）区分。
     */
    public enum LoginAuthenticateType {
        LOCAL,
        PLATFORM,
        DATA_SPECIALIST
    }

    /**
     * 平台管理用户前缀
     */
    public static final String PLATFORM_USER_PREFIX = "SA:";
    /**
     * 数据专员用户前缀
     */
    public static final String DATA_SPECIALIST_USER_PREFIX = "DS:";
}
