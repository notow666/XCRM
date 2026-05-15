package cn.cordys.mmba;

public final class MmbaConstants {

    private MmbaConstants() {
    }

    public static final String GROUP_BY_AUDIT = "GROUP_BY_AUDIT";
    public static final String GROUP_BY_COMMAND = "GROUP_BY_COMMAND";

    public static final String CALLBACK_PROCESS_PENDING = "PENDING";
    public static final String CALLBACK_PROCESS_SUCCESS = "SUCCESS";
    public static final String CALLBACK_PROCESS_FAILED = "FAILED";

    public static final String REQUEST_STATUS_INIT = "INIT";
    public static final String REQUEST_STATUS_SUCCESS = "SUCCESS";
    public static final String REQUEST_STATUS_FAILED = "FAILED";

    public static final String MEDIA_STORAGE_LOCAL = "LOCAL";
    public static final String MEDIA_SOURCE_MMBA_ASSET = "MMBA_ASSET";
    public static final String MEDIA_SOURCE_CALL_AUDIT_RECORD = "CALL_AUDIT_RECORD";

    public static final String SYSTEM_USER = "mmba_callback";

    /** SSE 载荷 type，与前端 {@code SSE_EVENT_MMBA_DEVICE_SYNC} 一致 */
    public static final String SSE_EVENT_DEVICE_SYNC = "MMBA_DEVICE_SYNC";
}
