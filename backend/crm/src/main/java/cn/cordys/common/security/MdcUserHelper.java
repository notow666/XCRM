package cn.cordys.common.security;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.security.SessionUser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import static cn.cordys.common.constants.MdcConstants.USER_ID_KEY;
import static cn.cordys.common.constants.MdcConstants.USER_NAME_KEY;
import static cn.cordys.common.constants.MdcConstants.USER_SOURCE_KEY;

/**
 * 按登录用户类型统一写入 MDC，供链路日志与操作日志使用。
 */
public final class MdcUserHelper {

    private MdcUserHelper() {
    }

    public static void apply(SessionUser user) {
        if (user == null || StringUtils.isBlank(user.getId())) {
            return;
        }
        MDC.put(USER_ID_KEY, resolveMdcUserId(user));
        if (StringUtils.isNotBlank(user.getName())) {
            MDC.put(USER_NAME_KEY, user.getName());
        }
        if (StringUtils.isNotBlank(user.getSource())) {
            MDC.put(USER_SOURCE_KEY, user.getSource());
        }
    }

    public static void clear() {
        MDC.remove(USER_ID_KEY);
        MDC.remove(USER_NAME_KEY);
        MDC.remove(USER_SOURCE_KEY);
    }

    static String resolveMdcUserId(SessionUser user) {
        String source = StringUtils.defaultString(user.getSource());
        if (LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name().equalsIgnoreCase(source)) {
            return DataSpecialistConstants.specialistUserId(user.getId());
        }
        if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(source)) {
            return LoginAuthenticateConstants.PLATFORM_USER_PREFIX + user.getId();
        }
        return user.getId();
    }
}
