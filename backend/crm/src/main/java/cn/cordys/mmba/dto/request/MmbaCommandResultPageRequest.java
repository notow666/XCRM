package cn.cordys.mmba.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import lombok.Data;

/**
 * MMBA 指令执行结果分页请求。
 */
@Data
public class MmbaCommandResultPageRequest extends BasePageRequest {
    private String keyword;
    private Integer behaviorType;
    private String reqId;
    private String um;
    private String targetValue;
    private String customerTel;
    private String friendPhone;
    private Integer processStatus;
    private Integer resultStatus;
}
