package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NumberCubeTaskCreateRequest {

    @NotBlank
    @Schema(description = "省份", requiredMode = Schema.RequiredMode.REQUIRED)
    private String province;

    @NotBlank
    @Schema(description = "城市", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @Schema(description = "选择模式: ALL/PARTIAL", defaultValue = "PARTIAL")
    private String selectionMode;

    @Schema(description = "选中号段ID(PARTIAL时必填)")
    private List<String> segmentIds;
}
