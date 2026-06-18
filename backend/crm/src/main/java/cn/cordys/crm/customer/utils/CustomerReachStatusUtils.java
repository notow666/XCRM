package cn.cordys.crm.customer.utils;

import cn.cordys.crm.customer.service.CustomerCallStatusService;

public final class CustomerReachStatusUtils {

    private CustomerReachStatusUtils() {
    }

    public static String formatCallStatus(Integer status) {
        int value = status == null ? CustomerCallStatusService.NOT_DIALED : status;
        return switch (value) {
            case CustomerCallStatusService.REQUEST_INITIATED -> "已发起拨打";
            case CustomerCallStatusService.DIALED_NOT_CONNECTED -> "拨打未接通";
            case CustomerCallStatusService.DIALED_CONNECTED -> "拨打已接通";
            default -> "未拨打";
        };
    }

    public static String formatWechatFriendStatus(Integer status) {
        int value = status == null ? 0 : status;
        return switch (value) {
            case -1 -> "已发起添加";
            case 1 -> "添加未通过";
            case 2 -> "已添加";
            default -> "未添加";
        };
    }
}
