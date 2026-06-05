package cn.cordys.crm.customer.constants;

import java.util.List;

public final class CustomerCreateSource {

    public static final String MANUAL_CREATE = "MANUAL_CREATE";
    public static final String PRIVATE_IMPORT = "PRIVATE_IMPORT";
    public static final String POOL_IMPORT = "POOL_IMPORT";
    public static final String CLUE_CREATE = "CLUE_CREATE";

    /**
     * 共享公海来源手机号校验的来源类型。定时删除只能按 POOL_IMPORT 判断，不能复用该集合。
     */
    private static final List<String> POOL_SOURCE_TYPES = List.of(POOL_IMPORT, CLUE_CREATE);

    public static boolean isPoolSource(String createSource) {
        return POOL_SOURCE_TYPES.contains(createSource);
    }

    public static List<String> poolSourceTypes() {
        return POOL_SOURCE_TYPES;
    }

    private CustomerCreateSource() {
    }
}
