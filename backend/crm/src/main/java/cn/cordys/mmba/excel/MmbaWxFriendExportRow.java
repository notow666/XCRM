package cn.cordys.mmba.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 管理员微信好友列表导出行。
 */
@Data
@ColumnWidth(20)
public class MmbaWxFriendExportRow {
    @ExcelProperty("UM")
    private String um;

    @ExcelProperty("员工姓名")
    private String staffName;

    @ExcelProperty("部门")
    @ColumnWidth(50)
    private String department;

    @ExcelProperty("设备名")
    private String deviceName;

    @ExcelProperty("微信账号ID")
    private String wxid;

    @ExcelProperty("微信账号")
    private String wxAccount;

    @ExcelProperty("微信手机号")
    private String wxPhone;

    @ExcelProperty("微信昵称")
    private String wxNickName;

    @ExcelProperty("微信登录状态")
    private String wxLoginStatus;

    @ExcelProperty("来源更新时间")
    private String sourceUpdateTime;

    @ExcelProperty("设备ID")
    private String deviceId;

    @ExcelProperty("登录状态")
    private String loginStatus;

    @ExcelProperty("好友总数")
    private Integer total;

    @ExcelProperty("好友微信号")
    private String contactImAppAccount;

    @ExcelProperty("好友微信ID")
    private String contactImIdInApp;

    @ExcelProperty("好友昵称")
    private String contactImAppNickName;

    @ExcelProperty("好友备注")
    private String contactImAppNote;

    @ExcelProperty("好友手机号")
    private String contactMobile;

    @ExcelProperty("添加好友手机号")
    private String friendPhone;

    @ExcelProperty("添加好友搜索内容")
    private String friendSearch;

    @ExcelProperty("地区")
    private String contactArea;

    @ExcelProperty("性别")
    private String contactSex;

    @ExcelProperty("头像")
    @ColumnWidth(60)
    private String contactImAppHeaderPic;

    @ExcelProperty("描述")
    @ColumnWidth(40)
    private String contactDescription;

    @ExcelProperty("导出时间")
    private String exportTime;
}
