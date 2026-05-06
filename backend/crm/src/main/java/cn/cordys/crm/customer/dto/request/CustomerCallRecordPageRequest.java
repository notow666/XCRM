package cn.cordys.crm.customer.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerCallRecordPageRequest extends BasePageRequest {

    @NotBlank(message = "客户ID不能为空")
    private String sourceId;
}
