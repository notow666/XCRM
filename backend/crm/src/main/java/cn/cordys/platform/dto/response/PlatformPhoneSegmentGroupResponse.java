package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlatformPhoneSegmentGroupResponse {

    @Schema(description = "号段前三位")
    private String prefix;

    @Schema(description = "号段数量")
    private Long count;
}
