package cn.cordys.crm.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class PoolBatchAssignRequest extends PoolBatchRequest {

    @Schema(description = "分配用户ID（单个，兼容旧接口）")
    private String assignUserId;

    @Schema(description = "分配用户ID列表（多选）")
    private List<String> assignUserIds;
}
