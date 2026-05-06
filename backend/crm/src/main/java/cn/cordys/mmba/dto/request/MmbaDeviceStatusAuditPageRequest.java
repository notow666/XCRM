package cn.cordys.mmba.dto.request;

import lombok.Data;

/**
 * 设备状态审计分页请求。
 */
@Data
public class MmbaDeviceStatusAuditPageRequest extends MmbaBaseAuditPageRequest {
    private Integer deviceStatus;
}
