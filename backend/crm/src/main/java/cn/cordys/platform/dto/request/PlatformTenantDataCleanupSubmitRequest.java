package cn.cordys.platform.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PlatformTenantDataCleanupSubmitRequest {

    @NotEmpty(message = "请选择租户")
    private List<String> tenantIds;

    @NotBlank(message = "请选择开始日期")
    private String startDate;

    @NotBlank(message = "请选择结束日期")
    private String endDate;
}
