package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NumberCubeSegmentQueryRequest {

    @NotBlank
    @Schema(description = "省份", requiredMode = Schema.RequiredMode.REQUIRED)
    private String province;

    @NotBlank
    @Schema(description = "城市", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @Schema(description = "号段前三位，可选")
    private String prefix;
}
