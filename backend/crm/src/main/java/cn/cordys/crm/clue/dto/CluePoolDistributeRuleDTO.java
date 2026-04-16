package cn.cordys.crm.clue.dto;

import cn.cordys.common.dto.condition.CombineSearch;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CluePoolDistributeRuleDTO {

    @Schema(description = "公海池ID")
    private String customerPoolId;

    @Schema(description = "组合查询规则")
    private CombineSearch combineSearch;
}
