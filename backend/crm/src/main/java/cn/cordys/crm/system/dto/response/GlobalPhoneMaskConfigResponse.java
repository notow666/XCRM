package cn.cordys.crm.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GlobalPhoneMaskConfigResponse {

    @Schema(description = "是否开启全局手机号脱敏")
    private Boolean enabled;
}
