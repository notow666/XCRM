package cn.cordys.crm.tools.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeTaskResponse {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "号段数量")
    private Integer segmentCount;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "明文打包文件ID")
    private String plainPackFileId;

    @Schema(description = "脱敏打包文件ID")
    private String maskedPackFileId;

    @Schema(description = "明文包是否可直接下载")
    private Boolean plainPackReady;

    @Schema(description = "脱敏包是否可直接下载")
    private Boolean maskedPackReady;

    @Schema(description = "创建时间")
    private Long createTime;

    @Schema(description = "更新时间")
    private Long updateTime;
}
