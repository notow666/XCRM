package cn.cordys.crm.system.dto.response;

import cn.cordys.excel.domain.ExcelErrData;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class ImportResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "成功数量")
    private int successCount;
    @Schema(description = "失败数量")
    private int failCount;
    @Schema(description = "报错信息")
    private List<ExcelErrData> errorMessages;
    @Schema(description = "错误文件ID")
    private String errorFileId;
    @Schema(description = "错误文件名称")
    private String errorFileName;
}
