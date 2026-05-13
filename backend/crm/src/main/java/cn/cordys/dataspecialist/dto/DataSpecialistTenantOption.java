package cn.cordys.dataspecialist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DataSpecialistTenantOption {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "租户编码")
    private String code;

    @Schema(description = "租户名称")
    private String name;
}
