package cn.cordys.crm.follow.dto.request;

import cn.cordys.common.domain.BaseModuleFieldValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FollowUpRecordAddRequest {

    @Size(max = 32)
    @Schema(description = "客户id")
    private String customerId;

    @Size(max = 32)
    @Schema(description = "商机id")
    private String opportunityId;

    @Size(max = 32)
    @Schema(description = "类型:CUSTOMER/CLUE")
    private String type;

    @Size(max = 32)
    @Schema(description = "线索id")
    private String clueId;

    @Size(max = 3000)
    @Schema(description = "跟进内容")
    private String content;

    @Schema(description = "跟进时间")
    private Long followTime;

    @Size(max = 32)
    @Schema(description = "跟进方式")
    private String followMethod;

    @Size(max = 32)
    @Schema(description = "负责人")
    private String owner;

    @Size(max = 32)
    @Schema(description = "联系人")
    private String contactId;

    @Size(max = 32)
    @Schema(description = "跟进结果: IN_PROGRESS-跟进中, COMPLETED-跟进完成, FAILED-无效")
    private String followResult;

    @Size(max = 32)
    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "自定义字段")
    private List<BaseModuleFieldValue> moduleFields;

}
