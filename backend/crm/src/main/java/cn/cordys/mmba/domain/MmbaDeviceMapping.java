package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_device_mapping")
public class MmbaDeviceMapping extends BaseModel {
    private String um;
    private String staffName;
    private String deviceId;
    private String imei;
    private String imei2;
    private String iccid;
    private String wxid;
    private String wxAccount;
    private String wxPhone;
    private String wxNickName;
    private String wxHeaderPic;
    private String qq;
    private String mappingStatus;
    private Long lastSyncTime;
}
