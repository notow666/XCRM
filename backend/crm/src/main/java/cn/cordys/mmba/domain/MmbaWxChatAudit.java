package cn.cordys.mmba.domain;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_wx_chat_audit")
public class MmbaWxChatAudit {
    private String appName;
    private Integer behaviorType;
    private Integer chatSourcesType;
    private Integer chatType;
    private String contactDescription;
    private String content;
    private String customer;
    private String customerAccount;
    private String customerAccountId;
    private String customerNickname;
    private String customerPic;
    private Long deptId;
    private String deptIdPath;
    private String deptInfo;
    private String deviceId;
    private Integer direction;
    private String duration;
    @Id
    private String esId;
    private String fileIdentifier;
    private String fileName;
    private String friendPhone;
    private String friendSearch;
    private String groupId;
    private String groupName;
    private String imei;
    private String imei2;
    private String insertTime;
    private String memberAccount;
    private String memberNickName;
    private String memberPic;
    private String msgId;
    private String orgName;
    private String orgNames;
    private String quoteId;
    private String reqId;
    private String staffAccount;
    private String staffAccountId;
    private String staffName;
    private String staffNickname;
    private String staffPic;
    private Integer status;
    private Integer targetType;
    private String tenancyName;
    private String time;
    private Long timestamp;
    private String um;
    private String contactMobile;
    private String rawData;
    private String callbackRecordId;
}
