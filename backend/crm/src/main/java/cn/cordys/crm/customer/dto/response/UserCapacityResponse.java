package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UserCapacityResponse {

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "库容上限")
    private Integer capacity;

    @Schema(description = "已拥有客户数")
    private Integer ownedCount;

    @Schema(description = "剩余库容（排除回款和无效客户阶段）")
    private Integer remainingCapacity;
}