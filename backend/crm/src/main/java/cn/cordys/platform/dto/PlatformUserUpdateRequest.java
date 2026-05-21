package cn.cordys.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformUserUpdateRequest {

    @Size(max = 128)
    @Schema(description = "昵称，不传则不修改")
    private String nickname;

    @Size(min = 1, max = 64)
    @Schema(description = "新密码，不传则不修改")
    private String password;

    @Size(max = 32)
    @Schema(description = "状态 ACTIVE / DISABLED")
    private String status;
}
