package cn.cordys.crm.system.dto.response;

import lombok.Data;

@Data
public class WechatAccountStatListResponse {
    private String id;
    private String um;
    private String employeeName;
    private String departmentName;
    private String deviceName;
    private Integer deviceStatus;
    private String deviceDisplay;
    private String wxHeaderPic;
    private String wxNickName;
    private String wxAccount;
    private String friendCount;
    private String chatRecordCount;
    private Long lastSyncTime;
    private Long updateTime;
}
