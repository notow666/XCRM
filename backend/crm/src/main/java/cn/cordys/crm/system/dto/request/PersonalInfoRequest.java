package cn.cordys.crm.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersonalInfoRequest {

    @Size(min = 6, max = 64, message = "{login_account_length}")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "{login_account_format_error}")
    @Schema(description = "登录账号")
    @NotBlank
    private String phone;

    @Pattern(regexp = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$", message = "{email.format_error}")
    @Schema(description = "邮箱")
    @NotBlank
    private String email;


}
