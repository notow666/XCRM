package cn.cordys.mmba.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指掌易管理平台 SSO 跳转地址（由前端整页打开）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MmbaMgmtSsoRedirectVO {
    private String url;
}
