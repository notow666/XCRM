package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerAutoDeleteSaveRequest {

    @NotNull
    @Min(1)
    @Max(365)
    @Schema(description = "多少天后删除")
    private Integer days;
}
