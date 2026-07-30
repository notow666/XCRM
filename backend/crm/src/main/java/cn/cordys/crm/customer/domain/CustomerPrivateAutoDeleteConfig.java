package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_private_auto_delete_config")
public class CustomerPrivateAutoDeleteConfig extends BaseModel {

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "多少个自然日后删除")
    private Integer days;
}
