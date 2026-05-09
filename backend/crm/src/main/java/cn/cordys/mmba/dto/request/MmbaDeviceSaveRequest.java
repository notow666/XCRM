package cn.cordys.mmba.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增 MMBA 设备（管理端）。
 */
@Data
public class MmbaDeviceSaveRequest {

    @NotBlank
    @Schema(description = "主键id = um")
    private String id;

    @Schema(description = "设备ID，可为空")
    private String deviceId;

    @Schema(description = "设备名称")
    private String deviceName;
    @Schema(description = "设备型号")
    private String deviceType;
    @Schema(description = "设备状态")
    private Integer deviceStatus;
    @Schema(description = "IMEI1")
    private String imei;
    @Schema(description = "IMEI2")
    private String imei2;
    @Schema(description = "ICCID1")
    private String iccid;
    @Schema(description = "ICCID2")
    private String iccid2;
    @Schema(description = "手机号1")
    private String phone;
    @Schema(description = "手机号2")
    private String phone2;
    @Schema(description = "运营商1")
    private String telecomOperators;
    @Schema(description = "运营商2")
    private String telecomOperators2;
    @Schema(description = "员工姓名")
    private String staffName;
    @Schema(description = "部门")
    private String orgName;
    @Schema(description = "部门路径")
    private String orgNames;
    @Schema(description = "最后上线时间（后端据此写入 lastOnline 毫秒戳）")
    private String lastOnlineTime;
    @Schema(description = "在线状态")
    private Integer loginStatus;
}
