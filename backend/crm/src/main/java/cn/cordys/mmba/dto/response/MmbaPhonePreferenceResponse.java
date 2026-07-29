package cn.cordys.mmba.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class MmbaPhonePreferenceResponse {

    @Schema(description = "默认拨号卡槽：1-卡槽1，2-卡槽2；空表示未设置")
    private Integer defaultCardSlotNum;

    @Schema(description = "当前用户是否已配置UM")
    private Boolean umConfigured;

    @Schema(description = "当前用户是否已配置MMBA设备")
    private Boolean deviceConfigured;

    @Schema(description = "当前用户的卡槽信息")
    private List<MmbaPhoneCardSlotResponse> cardSlots;
}
