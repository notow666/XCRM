package cn.cordys.dataspecialist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DataSpecialistCreateRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "登录名")
    private String username;

    @Size(max = 128)
    @Schema(description = "名称")
    private String name;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;

    @NotBlank
    @Size(min = 1, max = 64)
    @Schema(description = "初始密码")
    private String password;

    @NotEmpty
    @Schema(description = "可导入租户ID列表")
    private List<String> tenantIds;
}
