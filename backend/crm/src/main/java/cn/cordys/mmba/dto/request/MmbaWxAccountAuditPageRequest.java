package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 微信账号审计分页请求。
 */
@Data
public class MmbaWxAccountAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String staffIdInApp;
    private String staffImAppAccount;
}
