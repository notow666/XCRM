package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 短信审计分页请求。
 */
@Data
public class MmbaSmsRecordAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String customerTel;
    private Integer type;
}
