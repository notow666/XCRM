package cn.cordys.crm.tools.dto.response;



import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;



import java.util.List;



@Data

public class NumberCubeTaskDetailResponse {



    @Schema(description = "ID")

    private String id;



    @Schema(description = "省份")

    private String province;



    @Schema(description = "城市")

    private String city;



    @Schema(description = "选择模式: ALL/PARTIAL")

    private String selectionMode;



    @Schema(description = "号段数量")

    private Integer segmentCount;



    @Schema(description = "号段分组(按prefix汇总)")

    private List<PlatformPhoneSegmentGroupResponse> segmentGroups;



    @Schema(description = "任务状态")

    private String status;



    @Schema(description = "错误信息")

    private String errorMessage;



    @Schema(description = "创建时间")

    private Long createTime;



    @Schema(description = "更新时间")

    private Long updateTime;

}

