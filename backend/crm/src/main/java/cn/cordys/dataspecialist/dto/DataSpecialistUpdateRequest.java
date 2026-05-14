package cn.cordys.dataspecialist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DataSpecialistUpdateRequest {

    @Size(max = 128)
    @Schema(description = "名称，不传则不修改")
    private String name;

    @Size(max = 512)
    @Schema(description = "备注，不传则不修改")
    private String remark;

    @Size(min = 1, max = 64)
    @Schema(description = "新密码，不传则不修改")
    private String password;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "可导入租户ID列表（全量替换）")
    private List<String> tenantIds;
}
