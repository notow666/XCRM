package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeGenerateCallbackRequest {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "状态 SUCCESS/FAILED")
    private String status;

    @Schema(description = "新生成号段数")
    private Integer generated;

    @Schema(description = "跳过号段数")
    private Integer skipped;

    @Schema(description = "失败号段数")
    private Integer failed;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "回调令牌")
    private String token;
}
