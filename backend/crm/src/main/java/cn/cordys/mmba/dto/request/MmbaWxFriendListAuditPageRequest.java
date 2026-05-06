package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 微信好友列表审计分页请求。
 */
@Data
public class MmbaWxFriendListAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String staffIdInApp;
    private String contactMobile;
}
