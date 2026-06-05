package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PlatformForceLogoutRequest {

    @Schema(description = "登出倒计时秒数，默认60")
    @Min(value = 5, message = "graceSeconds不能小于5")
    @Max(value = 120, message = "graceSeconds不能大于120")
    private Integer graceSeconds = 60;
}
