package cn.cordys.crm.customer.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


/**
 * @author jianxing
 * @date 2025-02-08 16:24:22
 */
@Data
public class CustomerPageRequest extends BasePageRequest {

    @Schema(description = "公海ID{公海客户列表时传参}")
    private String poolId;

    @Schema(description = "是否查询关联客户列表")
    private Boolean transition;

    @Schema(description = "查询需要关联的公海客户传参")
    private List<String> transitionPoolIds;

	@Schema(description = "关联客户数据权限")
	private DeptDataPermissionDTO transitionDataPermission;

    @Schema(description = "仅返回可签约客户（新建合同客户选择器）：非公海 + 签约阶段 +（负责人为当前用户 或 存在未完成签约阶段跟进计划且处理人为当前用户）")
    private Boolean signableOnly;
}
