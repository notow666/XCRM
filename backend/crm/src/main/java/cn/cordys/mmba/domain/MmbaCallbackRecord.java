package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_callback_record")
public class MmbaCallbackRecord extends BaseModel {
    private Integer behaviorType;
    private String tenancyName;
    private Integer recordCount;
    private String payloadHash;
    private String payloadRaw;
    private String processStatus;
    private String processResult;
    private String errorMessage;
    private Integer retryCount;
}
