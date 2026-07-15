package cn.cordys.crm.tools.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NumberCubeTaskPageRequest extends BasePageRequest {

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "创建时间开始(毫秒)")
    private Long createTimeStart;

    @Schema(description = "创建时间结束(毫秒)")
    private Long createTimeEnd;
}
