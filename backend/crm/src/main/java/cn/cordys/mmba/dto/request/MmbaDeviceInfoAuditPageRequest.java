package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 设备信息审计分页请求。
 */
@Data
public class MmbaDeviceInfoAuditPageRequest extends MmbaBaseAuditPageRequest {
    private String phone;
    private String iccid;
    private String deviceType;
}
