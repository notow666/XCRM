package cn.cordys.crm.tools.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeDownloadStartResponse {

    @Schema(description = "导出任务ID；缓存命中时可为空")
    private String exportJobId;

    @Schema(description = "状态 PREPARED/RUNNING/SUCCESS")
    private String status;

    @Schema(description = "是否命中任务级打包缓存")
    private Boolean cached;

    @Schema(description = "缓存文件ID")
    private String fileId;
}
