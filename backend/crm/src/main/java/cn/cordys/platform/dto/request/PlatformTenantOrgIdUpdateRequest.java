package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformTenantOrgIdUpdateRequest {

    @Schema(description = "租户对应 mmba 平台部门 ID")
    @NotBlank(message = "orgId不能为空")
    @Size(max = 32, message = "orgId长度不能超过32")
    private String orgId;
}
