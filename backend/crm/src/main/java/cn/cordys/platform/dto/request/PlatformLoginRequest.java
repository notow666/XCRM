package cn.cordys.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlatformLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
