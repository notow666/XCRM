package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerBatchUpdateByConditionRequest extends CustomerPageRequest {

    @NotNull
    @Min(1)
    @Max(2000)
    @Schema(description = "本次编辑数量，单次最多 2000 条")
    private Integer updateCount;

    @NotBlank
    @Schema(description = "字段ID或字段key", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldId;

    @Schema(description = "字段值")
    private Object fieldValue;
}
