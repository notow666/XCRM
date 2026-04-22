package cn.cordys.crm.system.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "global_phone_mask_config")
public class GlobalPhoneMaskConfig extends BaseModel {

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "是否开启全局手机号脱敏")
    private Boolean enabled;
}
