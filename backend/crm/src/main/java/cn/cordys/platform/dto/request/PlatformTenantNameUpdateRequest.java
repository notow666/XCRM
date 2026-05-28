package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformTenantNameUpdateRequest {

    @Schema(description = "租户显示名称")
    @NotBlank(message = "name不能为空")
    @Size(max = 255, message = "name长度不能超过255")
    private String name;
}
