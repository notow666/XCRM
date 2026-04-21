package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_data_cleanup_config")
public class CustomerDataCleanupConfig extends BaseModel {

    @Schema(description = "租户ID")
    private String organizationId;

    @Schema(description = "需清理的字段ID列表(JSON)")
    private String fieldIds;

    @Schema(description = "多少天后清理")
    private Integer days;
}