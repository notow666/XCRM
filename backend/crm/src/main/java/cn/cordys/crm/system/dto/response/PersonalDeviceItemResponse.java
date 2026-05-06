package cn.cordys.crm.system.dto.response;

import lombok.Data;

@Data
public class PersonalDeviceItemResponse {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String phone1;
    private String phone2;
    private String telecomOperators1;
    private String telecomOperators2;
    private String imei1;
    private String imei2;
    private String iccid1;
    private String iccid2;
    private Long updateTime;
}
