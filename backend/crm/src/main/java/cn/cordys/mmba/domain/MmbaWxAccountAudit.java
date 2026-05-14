package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_wx_account_audit")
public class MmbaWxAccountAudit {
    private Long appId;
    private String appName;
    private Integer behaviorType;
    private String createTime;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    @Id
    private String esId;
    private String imei;
    private String imei2;
    private String insertTime;
    private String note;
    private String orgName;
    private String orgNames;
    private String qq;
    private String sign;
    private String staffArea;
    private String staffIdInApp;
    private String staffImAppAccount;
    private String staffImAppHeaderPic;
    private String staffImNickName;
    private String staffMobile;
    private String staffName;
    private Integer staffSex;
    private String tenancyName;
    private Long timestamp;
    private String um;
    private Integer verifiedStatus;
    private String callbackRecordId;
}
