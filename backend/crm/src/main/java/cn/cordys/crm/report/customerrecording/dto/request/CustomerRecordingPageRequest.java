package cn.cordys.crm.report.customerrecording.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerRecordingPageRequest extends BasePageRequest {

    @NotNull(message = "通话开始时间不能为空")
    private Long startTime;

    @NotNull(message = "通话结束时间不能为空")
    private Long endTime;

    private String departmentId;

    private String employeeId;

    private String customerName;

    private String customerTel;

    @Min(value = 0, message = "最短通话时长不能小于0")
    private Integer minDuration;

    @Min(value = 0, message = "最长通话时长不能小于0")
    private Integer maxDuration;
}
