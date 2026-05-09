package cn.cordys.mmba.excel;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 与设备导出 Excel 表头对齐。
 */
@Data
public class MmbaDeviceImportRow {
    @ExcelProperty("用户名")
    private String um;
    @ExcelProperty("姓名")
    private String staffName;
    @ExcelProperty("部门")
    private String orgName;
    @ExcelProperty("状态")
    private String umStatus;
}
