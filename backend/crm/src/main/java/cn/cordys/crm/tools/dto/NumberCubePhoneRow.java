package cn.cordys.crm.tools.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class NumberCubePhoneRow {

    @ExcelProperty("phone")
    private String phone;
}
