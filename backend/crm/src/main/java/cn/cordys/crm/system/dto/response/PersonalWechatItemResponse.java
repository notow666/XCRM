package cn.cordys.crm.system.dto.response;

import lombok.Data;

@Data
public class PersonalWechatItemResponse {
    private String wxNickName;
    private String wxId;
    private String wxAccount;
    private String wxPhone;
    private String mappingStatus;
    private Long updateTime;
}
