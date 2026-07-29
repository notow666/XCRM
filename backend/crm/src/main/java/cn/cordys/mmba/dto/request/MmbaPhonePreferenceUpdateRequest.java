package cn.cordys.mmba.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MmbaPhonePreferenceUpdateRequest {

    @Schema(description = "默认拨号卡槽：1-卡槽1，2-卡槽2；不传或传空表示取消默认卡")
    private Integer defaultCardSlotNum;
}
