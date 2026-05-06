package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 微信好友变更审计分页请求。
 */
@Data
public class MmbaWxFriendChangeAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String friendPhone;
    private String staffIdInApp;
    private Integer operFlag;
}
