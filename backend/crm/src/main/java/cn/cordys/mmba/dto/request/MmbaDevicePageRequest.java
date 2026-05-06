package cn.cordys.mmba.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import lombok.Data;

/**
 * 设备主表分页请求。
 */
@Data
public class MmbaDevicePageRequest extends BasePageRequest {
    private String keyword;
    private String um;
    private String deviceId;
    private String imei;
    private String phone;
    private String staffName;
    private Integer deviceStatus;
}
