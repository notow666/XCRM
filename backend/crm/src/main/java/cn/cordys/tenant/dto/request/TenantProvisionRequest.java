package cn.cordys.tenant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TenantProvisionRequest {

    @Schema(description = "租户编码，用于路由与库名 crm_tenant_{code}，仅字母数字下划线与中划线")
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{1,62}$")
    private String code;

    @Schema(description = "租户显示名称")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(description = "除默认操作人外，额外绑定到该租户的用户 ID，与操作人去重")
    @Size(max = 50)
    private List<String> initialUserIds;
}
