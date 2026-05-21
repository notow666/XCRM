package cn.cordys.platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOverviewSeriesItem {
    @Schema(description = "名称")
    private String name;
    @Schema(description = "数值")
    private Long value;
}
