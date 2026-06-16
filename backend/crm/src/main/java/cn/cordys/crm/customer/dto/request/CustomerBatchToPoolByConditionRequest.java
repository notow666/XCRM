package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerBatchToPoolByConditionRequest extends CustomerPageRequest {

    @NotNull
    @Min(1)
    @Max(2000)
    @Schema(description = "本次移入公海数量，单次最多 2000 条")
    private Integer toPoolCount;

    @Schema(description = "目标公海ID")
    private String targetPoolId;

    @Schema(description = "移入原因ID")
    private String reasonId;
}
