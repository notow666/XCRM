package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PoolBatchAssignByConditionRequest extends CustomerPageRequest {

    @NotNull
    @Min(1)
    @Max(2000)
    @Schema(description = "本次分配数量，单次最多 2000 条")
    private Integer assignCount;

    @Schema(description = "分配用户ID（单个，兼容旧接口）")
    private String assignUserId;

    @Schema(description = "分配用户ID列表（多选）")
    private List<String> assignUserIds;
}
