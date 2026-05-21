package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_auto_delete_config")
public class CustomerAutoDeleteConfig extends BaseModel {

    @Schema(description = "租户ID")
    private String organizationId;

    @Schema(description = "多少天后删除")
    private Integer days;
}
