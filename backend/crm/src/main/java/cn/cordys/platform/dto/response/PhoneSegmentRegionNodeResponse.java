package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class PhoneSegmentRegionNodeResponse {

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "值")
    private String value;

    @Schema(description = "子节点")
    private List<PhoneSegmentRegionNodeResponse> children;
}
