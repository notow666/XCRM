package cn.cordys.crm.tools.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class NumberCubeTaskSegmentDetailResponse {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "号段前三位")
    private String prefix;

    @Schema(description = "号段值列表")
    private List<String> segments;
}
