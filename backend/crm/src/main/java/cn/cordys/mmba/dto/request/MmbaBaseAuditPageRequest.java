package cn.cordys.mmba.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import lombok.Data;

/**
 * MMBA 审计列表通用分页请求。
 */
@Data
public class MmbaBaseAuditPageRequest extends BasePageRequest {
    private String keyword;
    private String reqId;
    private String um;
    private String deviceId;
    private String imei;
    private String staffName;
}
