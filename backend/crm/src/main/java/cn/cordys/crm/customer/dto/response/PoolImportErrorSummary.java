package cn.cordys.crm.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "公海导入错误分类统计")
public class PoolImportErrorSummary {

    @Schema(description = "字段校验失败数（红色）")
    private int fieldValidationCount;

    @Schema(description = "Excel内手机号重复数（黄色）")
    private int excelDuplicateCount;

    @Schema(description = "客户池冲突数（橙色）")
    private int privateConflictCount;

    @Schema(description = "其他公海池冲突数（蓝色）")
    private int otherPoolConflictCount;
}