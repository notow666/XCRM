package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 通话审计分页请求。
 */
@Data
public class MmbaCallRecordAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String customerTel;
}
