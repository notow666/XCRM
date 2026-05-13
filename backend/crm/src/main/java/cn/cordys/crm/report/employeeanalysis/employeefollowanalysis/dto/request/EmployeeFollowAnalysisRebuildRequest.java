package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmployeeFollowAnalysisRebuildRequest {

    @NotBlank
    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;

    @NotBlank
    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;
}
