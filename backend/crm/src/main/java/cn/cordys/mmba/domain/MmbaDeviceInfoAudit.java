package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_device_info_audit")
public class MmbaDeviceInfoAudit {
    @Id
    private String id;
    private Integer behaviorType;
    private String changeTime;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    private String deviceType;
    private String iccid;
    private String iccid2;
    private String imei;
    private String imei2;
    private String orgName;
    private String orgNames;
    private String phone;
    private String phone2;
    private String staffName;
    private String telecomOperators;
    private String telecomOperators2;
    private String tenancyName;
    private Long timestamp;
    private String um;
    private String callbackRecordId;
}
