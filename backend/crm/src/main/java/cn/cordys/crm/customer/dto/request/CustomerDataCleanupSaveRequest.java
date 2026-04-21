package cn.cordys.crm.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CustomerDataCleanupSaveRequest {

    @NotNull
    @Schema(description = "需清理的字段ID列表")
    private List<String> fieldIds;

    @NotNull
    @Schema(description = "多少天后清理")
    private Integer days;
}
