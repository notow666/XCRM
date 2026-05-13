package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_wx_login_audit")
public class MmbaWxLoginAudit {
    @Id
    private String id;
    private String appPkgName;
    private Integer behaviorType;
    private Long createTime;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    private String deviceName;
    private Integer loginStatus;
    private String orgName;
    private String orgNames;
    private String staffIdInApp;
    private String staffImAppAccount;
    private String staffName;
    private String tenancyName;
    private String um;
    private String callbackRecordId;
}
