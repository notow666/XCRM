package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_sms_record_audit")
public class MmbaSmsRecordAudit {
    private Integer behaviorType;
    private String tenancyName;
    private String bizExtInfo;
    private Integer cardSlotNum;
    private String content;
    private String createTime;
    private String customer;
    private String customerTel;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    private Integer direction;
    @Id
    private String esId;
    private String iccid;
    private String iccidPhone;
    private String imei;
    private String imei2;
    private String insertTime;
    private Boolean netSms;
    private String orgName;
    private String orgNames;
    private String reqId;
    private Integer sentStatus;
    private String staffName;
    private String subject;
    private Long timestamp;
    private Integer type;
    private String um;
    private String callbackRecordId;
}
