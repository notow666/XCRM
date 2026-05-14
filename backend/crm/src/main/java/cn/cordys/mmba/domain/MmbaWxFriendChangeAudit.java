package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_wx_friend_change_audit")
public class MmbaWxFriendChangeAudit {
    private Integer behaviorType;
    private String contactArea;
    private String contacImAppNote;
    private String contactDescription;
    private String contactImAppAccount;
    private String contactImAppHeaderPic;
    private String contactImAppNickName;
    private String contactImAppNote;
    private String contactImIdInApp;
    private String contactMobile;
    private String contactSex;
    private String contactWeixinTags;
    private String createTime;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    @Id
    private String esId;
    private String friendPhone;
    private String friendSearch;
    private String imei;
    private String imei2;
    private String insertTime;
    private String isFriend;
    private Integer operFlag;
    private String orgName;
    private String orgNames;
    private String source;
    private String staffIdInApp;
    private String staffName;
    private String tenancyName;
    private Long timestamp;
    private String um;
    private String wxType;
    private String callbackRecordId;
}
