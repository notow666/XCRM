package cn.cordys.crm.tools.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeDownloadProgressResponse {

    @Schema(description = "导出任务ID")
    private String exportJobId;

    @Schema(description = "状态 PREPARED/SUCCESS/ERROR")
    private String status;

    @Schema(description = "总号段数")
    private Integer total;

    @Schema(description = "已处理号段数")
    private Integer processed;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "文件名")
    private String fileName;
}
