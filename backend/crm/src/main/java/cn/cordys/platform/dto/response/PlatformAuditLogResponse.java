package cn.cordys.platform.dto.response;

import lombok.Data;

@Data
public class PlatformAuditLogResponse {
    private String id;
    private String operatorId;
    private String action;
    private String tenantId;
    private String result;
    private String detail;
    private Long durationMs;
    private Long createTime;
}
