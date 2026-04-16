package cn.cordys.crm.clue.dto.request;

import cn.cordys.common.domain.BaseModuleFieldValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 三方推送线索
 * @author hql
 * @date 2026-04-09 15:24:22
 */
@Data
public class CluePushRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "线索池Id")
    private String poolId;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "客户姓名")
    private String name;

    @NotBlank
    @Size(max = 30)
    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "模块字段值")
    private List<BaseModuleFieldValue> moduleFields;
}