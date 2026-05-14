package cn.cordys.dataspecialist.domain;

import cn.cordys.common.domain.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "data_specialist")
public class DataSpecialist extends BaseModel {

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "名称")
    private String specialistName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "密码摘要")
    private String passwordHash;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
