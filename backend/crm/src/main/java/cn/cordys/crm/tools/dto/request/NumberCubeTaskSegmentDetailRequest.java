package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NumberCubeTaskSegmentDetailRequest {

    @NotBlank
    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @NotBlank
    @Schema(description = "号段前三位", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prefix;
}
