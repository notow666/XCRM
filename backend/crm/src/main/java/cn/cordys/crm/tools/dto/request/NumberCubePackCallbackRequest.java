package cn.cordys.crm.tools.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubePackCallbackRequest {

    @Schema(description = "导出任务ID")
    private String exportJobId;

    @Schema(description = "文件ID")
    private String fileId;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "状态 SUCCESS/ERROR")
    private String status;

    @Schema(description = "号段总数")
    private Integer total;

    @Schema(description = "已处理号段数")
    private Integer processed;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "回调令牌")
    private String token;
}
