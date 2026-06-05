package cn.cordys.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PlatformAnnouncementPageRequest {

    @Min(1)
    @Schema(description = "当前页，从 1 开始")
    private int current = 1;

    @Min(1)
    @Max(100)
    @Schema(description = "每页条数")
    private int pageSize = 5;
}
