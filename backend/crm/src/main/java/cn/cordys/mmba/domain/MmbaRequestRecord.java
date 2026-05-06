package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_request_record")
public class MmbaRequestRecord extends BaseModel {
    private String bizType;
    private String reqId;
    private String traceId;
    private String mmbaUrl;
    private String requestHeaders;
    private String requestBody;
    private String responseBody;
    private Integer responseCode;
    private String responseMessage;
    private String requestStatus;
    private String rawResult;
}
