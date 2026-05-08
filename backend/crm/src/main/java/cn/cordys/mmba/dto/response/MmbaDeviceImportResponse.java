package cn.cordys.mmba.dto.response;

import cn.cordys.excel.domain.ExcelErrData;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class MmbaDeviceImportResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "成功数量")
    private int successCount;
    @Schema(description = "失败数量")
    private int failCount;
    @Schema(description = "错误明细")
    private List<ExcelErrData> errorMessages;
}
