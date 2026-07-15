package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlatformPhoneSegmentResponse {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "号段")
    private String segment;

    @Schema(description = "号段前三位")
    private String prefix;

    @Schema(description = "运营商")
    private String isp;

    @Schema(description = "地区编码")
    private String areaCode;
}
