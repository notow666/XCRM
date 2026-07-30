package cn.cordys.crm.customer.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "customer_contract_delete_policy")
public class CustomerContractDeletePolicy extends BaseModel {

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "合同删除策略：CASCADE、KEEP_CONTRACT")
    private String policy;
}
