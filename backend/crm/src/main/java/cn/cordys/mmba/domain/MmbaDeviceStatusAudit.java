package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_device_status_audit")
public class MmbaDeviceStatusAudit {
    @Id
    private String id;
    private Integer behaviorType;
    private String changeTime;
    private String deviceId;
    private Integer deviceStatus;
    private String imei;
    private String imei2;
    private String tenancyName;
    private String um;
    private String rawData;
    private String callbackRecordId;
}
