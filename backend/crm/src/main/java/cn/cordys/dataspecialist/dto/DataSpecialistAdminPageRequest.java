package cn.cordys.dataspecialist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DataSpecialistAdminPageRequest {

    @Min(1)
    @Schema(description = "页码")
    private int current = 1;

    @Min(1)
    @Schema(description = "每页条数")
    private int pageSize = 10;

    @Schema(description = "用户名关键字")
    private String keyword;
}
