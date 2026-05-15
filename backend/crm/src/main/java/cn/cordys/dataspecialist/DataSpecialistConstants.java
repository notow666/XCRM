package cn.cordys.dataspecialist;

import org.apache.commons.lang3.StringUtils;

/**
 * 数据专员：写入租户库业务表时的 userId / create_user 等与租户内用户区分的前缀。 dataspecialist
 */
public final class DataSpecialistConstants {

    public static final String USER_ID_PREFIX = "DS:";

    public static final String SESSION_SOURCE = "DATA_SPECIALIST";

    public static final String PERMISSION_POOL_IMPORT = "DATA_SPECIALIST:POOL_IMPORT";

    /**
     * 公海导入完成后，经 SSE 推送给当前数据专员客户端的 JSON 字段 {@code type} 取值（与租户站内通知通道分离）。
     */
    public static final String SSE_POOL_IMPORT_RESULT_TYPE = "DATA_SPECIALIST_POOL_IMPORT";

    private DataSpecialistConstants() {
    }

    public static String specialistUserId(String masterSpecialistId) {
        return USER_ID_PREFIX + masterSpecialistId;
    }

    public static boolean isSpecialistUserId(String userId) {
        return userId != null && userId.startsWith(USER_ID_PREFIX);
    }

    /**
     * 业务侧带 DS: 前缀的 userId 转 master 库专员主键；非专员格式则原样 trim。
     */
    public static String masterSpecialistIdFromBusinessUserId(String userId) {
        if (!isSpecialistUserId(userId)) {
            return StringUtils.trimToNull(userId);
        }
        return userId.substring(USER_ID_PREFIX.length());
    }
}
