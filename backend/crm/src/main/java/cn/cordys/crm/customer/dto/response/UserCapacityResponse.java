package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Comparator;

@Data
public class UserCapacityResponse {

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "部门ID")
    private String departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "库容上限")
    private Integer capacity;

    @Schema(description = "已拥有客户数")
    private Integer ownedCount;

    @Schema(description = "剩余库容（排除回款和无效客户阶段）")
    private Integer remainingCapacity;


    public static class UserCapacityComparator implements Comparator<UserCapacityResponse> {
        @Override
        public int compare(UserCapacityResponse o1, UserCapacityResponse o2) {
            if(o1.getRemainingCapacity() == null) {
                return 1;
            }
            else if(o2.getRemainingCapacity() == null) {
                return -1;
            }
            return o1.getRemainingCapacity() - o2.getRemainingCapacity();
        }
    }
}
