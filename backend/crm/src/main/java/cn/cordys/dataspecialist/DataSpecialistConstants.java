package cn.cordys.dataspecialist;

/**
 * 数据专员：写入租户库业务表时的 userId / create_user 等与租户内用户区分的前缀。 dataspecialist
 */
public final class DataSpecialistConstants {

    public static final String USER_ID_PREFIX = "DS:";

    public static final String SESSION_SOURCE = "DATA_SPECIALIST";

    public static final String PERMISSION_POOL_IMPORT = "DATA_SPECIALIST:POOL_IMPORT";

    private DataSpecialistConstants() {
    }

    public static String specialistUserId(String masterSpecialistId) {
        return USER_ID_PREFIX + masterSpecialistId;
    }

    public static boolean isSpecialistUserId(String userId) {
        return userId != null && userId.startsWith(USER_ID_PREFIX);
    }
}
