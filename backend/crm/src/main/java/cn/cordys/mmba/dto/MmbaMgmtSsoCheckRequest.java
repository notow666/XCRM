package cn.cordys.mmba.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 指掌易管理平台调用客户 checkLoginUrl 时的请求体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MmbaMgmtSsoCheckRequest {
    private String token;
    private Long time;
}
