package cn.cordys.mmba.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员微信好友列表导出请求。
 */
@Data
public class MmbaWxFriendExportRequest {

    @NotBlank(message = "导出目录不能为空")
    private String outputDir;

    @NotBlank(message = "来源CSV文件不能为空")
    private String sourceCsvFile;

    @Min(value = 1, message = "最大微信号数量不能小于 1")
    @Max(value = 3000, message = "最大微信号数量不能超过 3000")
    private Integer maxWxAccounts;

    @Min(value = 1, message = "单微信号最大页数不能小于 1")
    @Max(value = 200, message = "单微信号最大页数不能超过 200")
    private Integer maxPagesPerWx;

    @Min(value = 300, message = "请求间隔不能小于 300 毫秒")
    private Integer intervalMillis;
}
