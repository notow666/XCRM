package cn.cordys.mmba.dto.response;

import lombok.Data;

/**
 * 管理员微信好友列表导出启动结果。
 */
@Data
public class MmbaWxFriendExportResponse {
    private String taskId;
    private String status;
    private String outputFile;
    private String statusFile;
    private String sourceCsvFile;
    private String failureCsvFile;
    private Integer maxWxAccounts;
    private Integer maxPagesPerWx;
    private Integer intervalMillis;
}
