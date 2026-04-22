package cn.cordys.crm.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GlobalPhoneMaskConfigRequest {

    @NotNull
    @Schema(description = "是否开启全局手机号脱敏")
    private Boolean enabled;
}
