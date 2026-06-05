package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlatformSystemAnnouncementItemResponse {

    @Schema(description = "公告ID")
    private String id;

    @Schema(description = "标题")
    private String subject;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "操作人ID")
    private String operatorId;

    @Schema(description = "创建时间")
    private Long createTime;
}
