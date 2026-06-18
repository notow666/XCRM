package cn.cordys.crm.customer.utils;

import cn.cordys.common.util.TimeUtils;
import cn.cordys.crm.customer.dto.response.CustomerListResponse;

import java.util.LinkedHashMap;

public class CustomerFieldUtils {

    public static LinkedHashMap<String, Object> getSystemFieldMap(CustomerListResponse data) {
        LinkedHashMap<String, Object> systemFieldMap = new LinkedHashMap<>();
        systemFieldMap.put("name", data.getName());
        systemFieldMap.put("mobile", data.getMobile());
        systemFieldMap.put("callStatus", getCallStatusText(data.getCallStatus()));
        systemFieldMap.put("wechatFriendStatus", getWechatFriendStatusText(data.getWechatFriendStatus()));
        systemFieldMap.put("owner", data.getOwnerName());
        systemFieldMap.put("collectionTime", TimeUtils.getDateTimeStr(data.getCollectionTime()));
        systemFieldMap.put("createUser", data.getCreateUserName());
        systemFieldMap.put("createTime", TimeUtils.getDateTimeStr(data.getCreateTime()));
        systemFieldMap.put("updateUser", data.getUpdateUserName());
        systemFieldMap.put("updateTime", TimeUtils.getDateTimeStr(data.getUpdateTime()));
        systemFieldMap.put("follower", data.getFollowerName());
        systemFieldMap.put("followTime", TimeUtils.getDateTimeStr(data.getFollowTime()));
        systemFieldMap.put("reservedDays", data.getReservedDays());
        systemFieldMap.put("recyclePoolName", data.getRecyclePoolName());
        systemFieldMap.put("departmentId", data.getDepartmentName());
        systemFieldMap.put("callStatus", CustomerReachStatusUtils.formatCallStatus(data.getCallStatus()));
        systemFieldMap.put("wechatFriendStatus", CustomerReachStatusUtils.formatWechatFriendStatus(data.getWechatFriendStatus()));
        return systemFieldMap;
    }

    private static String getCallStatusText(Integer status) {
        if (status == null) {
            return "未拨打";
        }
        switch (status) {
            case -1:
                return "已发起拨打";
            case 1:
                return "拨打未接通";
            case 2:
                return "拨打已接通";
            default:
                return "未拨打";
        }
    }

    private static String getWechatFriendStatusText(Integer status) {
        if (status == null) {
            return "未添加";
        }
        switch (status) {
            case -1:
                return "已发起添加";
            case 1:
                return "添加未通过";
            case 2:
                return "已添加";
            default:
                return "未添加";
        }
    }

}
