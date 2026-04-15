package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "公海导入预检查响应")
public class PoolCustomerImportCheckResponse {

    @Schema(description = "是否校验通过")
    private boolean passed;

    @Schema(description = "总行数")
    private int totalCount;

    @Schema(description = "有效行数")
    private int successCount;

    @Schema(description = "错误行数")
    private int errorCount;

    @Schema(description = "错误分类统计")
    private PoolImportErrorSummary errorSummary;

    @Schema(description = "错误Excel文件ID，用于下载")
    private String errorFileId;

    @Schema(description = "错误Excel文件名")
    private String errorFileName;
}