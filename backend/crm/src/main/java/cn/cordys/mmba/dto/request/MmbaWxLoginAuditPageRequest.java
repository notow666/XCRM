package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 微信登录登出审计分页请求。
 */
@Data
public class MmbaWxLoginAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String staffIdInApp;
    private String staffImAppAccount;
    private String deviceName;
    private Integer loginStatus;
}
