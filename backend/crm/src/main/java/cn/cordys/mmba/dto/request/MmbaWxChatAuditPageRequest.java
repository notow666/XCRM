package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 微信聊天审计分页请求。
 */
@Data
public class MmbaWxChatAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String friendPhone;
    private String customerAccount;
    private String customerAccountId;
    private Integer chatType;
}
