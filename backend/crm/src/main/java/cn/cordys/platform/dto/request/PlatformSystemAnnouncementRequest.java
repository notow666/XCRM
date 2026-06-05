package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformSystemAnnouncementRequest {

    @Schema(description = "公告标题")
    @NotBlank(message = "subject不能为空")
    @Size(max = 512, message = "subject长度不能超过512")
    private String subject;

    @Schema(description = "公告内容")
    @NotBlank(message = "content不能为空")
    @Size(max = 10000, message = "content长度不能超过10000")
    private String content;
}
