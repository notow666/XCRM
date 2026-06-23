package cn.cordys.crm.customer.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GlobalPoolCustomerPageRequest extends BasePageRequest {

    @Schema(description = "手机号码")
    @NotBlank
    @Size(max = 30)
    private String mobile;
}
