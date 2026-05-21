package cn.cordys.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformUserCreateRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "登录名")
    private String username;

    @Size(max = 128)
    @Schema(description = "昵称")
    private String nickname;

    @NotBlank
    @Size(min = 1, max = 64)
    @Schema(description = "初始密码")
    private String password;
}
