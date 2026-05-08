package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerSendWechatRequest {

    @NotBlank
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    @NotBlank
    @Size(max = 2048)
    @Schema(description = "微信消息内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
