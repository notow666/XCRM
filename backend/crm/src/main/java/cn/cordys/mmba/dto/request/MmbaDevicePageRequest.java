package cn.cordys.mmba.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import lombok.Data;

/**
 * 设备主表分页请求。
 */
@Data
public class MmbaDevicePageRequest extends BasePageRequest {
    private String keyword;
    private Integer deviceStatus;
}
