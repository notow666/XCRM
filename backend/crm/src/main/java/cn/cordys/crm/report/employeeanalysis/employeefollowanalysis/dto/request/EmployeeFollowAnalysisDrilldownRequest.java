package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeFollowAnalysisDrilldownRequest extends BasePageRequest {

    @NotBlank
    @Schema(description = "统计时间预设 today/yesterday/week/month/custom")
    private String timePreset;

    @Schema(description = "自定义开始时间毫秒值")
    private Long startTime;

    @Schema(description = "自定义结束时间毫秒值")
    private Long endTime;

    @NotBlank
    @Schema(description = "统计维度 employeeName/employeeDept/customerSource/statDay/statMonth")
    private String dimensionType;

    @NotBlank
    @Schema(description = "维度键")
    private String dimensionKey;

    @Schema(description = "部门ID，查询该部门及下级部门")
    private String departmentId;

    @NotBlank
    @Schema(description = "指标类型 inboundCustomer/contactedCustomer/newWechatFriends/dialCount/connectedCount/callOver1Min/callOver3Min/customDurationCall")
    private String metricType;

    @Min(value = 1, message = "自定义通话时长不能小于1秒")
    @Schema(description = "今日自定义通话时长秒数")
    private Integer customCallDurationSec;
}
