package cn.cordys.crm.tools.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeTaskProgressResponse {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "总号段数")
    private Integer total;

    @Schema(description = "已处理号段数")
    private Integer processed;

    @Schema(description = "新生成数")
    private Integer generated;

    @Schema(description = "跳过数")
    private Integer skipped;

    @Schema(description = "失败数")
    private Integer failed;
}
