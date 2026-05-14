package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_call_record_audit")
public class MmbaCallRecordAudit {
    private Integer behaviorType;
    private String tenancyName;
    private String answerTime;
    private Long answerTimestamp;
    private String beginTime;
    private Long beginTimestamp;
    private String bizExtInfo;
    private String callStatus;
    private Integer callType;
    private String customer;
    private String customerTel;
    private String customerId;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    private Integer direction;
    private Integer duration;
    private String endTime;
    private Long endTimestamp;
    @Id
    private String esId;
    private String iccid;
    private String iccidPhone;
    private String imei;
    private String imei2;
    private String insertTime;
    private Integer isConnected;
    private Integer mobileVendor;
    private String orgName;
    private String orgNames;
    private String phoneLocation;
    private String record;
    private String reqId;
    private Integer retry;
    private Integer ringDuration;
    private Integer soundChannel;
    private String staffName;
    private Long timestamp;
    private String um;
    private String callbackRecordId;
}
