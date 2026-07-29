package cn.cordys.mmba.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MmbaPhoneCardSlotResponse {

    @Schema(description = "卡槽编号：1-卡槽1，2-卡槽2")
    private Integer cardSlotNum;

    @Schema(description = "卡槽是否可用")
    private Boolean available;

    @Schema(description = "卡槽手机号")
    private String phone;
}
