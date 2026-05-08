package cn.cordys.mmba.excel;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 与设备导出 Excel 表头对齐（见 docs/deviceExport_*.xlsx）。
 */
@Data
public class MmbaDeviceImportRow {

    @ExcelProperty("设备名称")
    private String deviceName;
    @ExcelProperty("设备型号")
    private String deviceType;
    @ExcelProperty("用户名")
    private String um;
    @ExcelProperty("姓名")
    private String staffName;
    @ExcelProperty("部门")
    private String orgNames;
    @ExcelProperty("手机号")
    private String phone;
    @ExcelProperty("运营商")
    private String telecomOperators;
    @ExcelProperty("最后上线时间")
    private String lastOnlineTime;
    @ExcelProperty("IMEI")
    private String imei;
    @ExcelProperty("IMEI2")
    private String imei2;
    @ExcelProperty("ICCID")
    private String iccid;
    /** 数字 1/3/… 或中文：正常、预注册等 */
    @ExcelProperty("设备状态")
    private String deviceStatus;
    /** 数字 0/1 或中文：在线、离线 */
    @ExcelProperty("在线状态")
    private String loginStatus;
}
