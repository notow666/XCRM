package cn.cordys.crm.tools.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "number_cube_task")
public class NumberCubeTask extends BaseModel {

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "选择模式: ALL/PARTIAL")
    private String selectionMode;

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
}
