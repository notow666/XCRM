package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerFailReasonUpdateRequest {

    @NotBlank(message = "ID不能为空")
    @Schema(description = "ID")
    private String id;

    @NotBlank(message = "原因名称不能为空")
    @Schema(description = "原因名称")
    private String name;
}
