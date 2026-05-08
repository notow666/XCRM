package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_device")
public class MmbaDevice extends BaseModel {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private Integer deviceStatus;
    private String imei;
    private String imei2;
    private String iccid;
    private String iccid2;
    private String phone;
    private String phone2;
    private String telecomOperators;
    private String telecomOperators2;
    private String um;
    private String staffName;
    private String orgName;
    private String orgNames;
    private Long lastOnline;
    private String lastOnlineTime;
    private Integer loginStatus;
    private Integer lastBehaviorType;
    private Long lastAuditTime;
    private String rawData;
}
