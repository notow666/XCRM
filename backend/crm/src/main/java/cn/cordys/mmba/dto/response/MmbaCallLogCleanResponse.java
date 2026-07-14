package cn.cordys.mmba.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MmbaCallLogCleanResponse {
    private int selectedCount;
    private int issuedCount;
    private int skippedCount;
    private int failedCount;
}
