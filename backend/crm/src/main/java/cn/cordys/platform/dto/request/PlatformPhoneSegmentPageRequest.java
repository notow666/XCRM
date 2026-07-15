package cn.cordys.platform.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlatformPhoneSegmentPageRequest extends BasePageRequest {

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;
}
