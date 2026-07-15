package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class NumberCubeTaskProgressBatchRequest {

    @NotEmpty
    @Schema(description = "任务ID列表")
    private List<String> taskIds;
}
