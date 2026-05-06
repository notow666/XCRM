package cn.cordys.mmba;

import cn.cordys.common.exception.GenericException;
import lombok.Getter;

/**
 * MMBA 调用异常。
 * 用于在底层调用链中透传 MMBA 的原始响应内容，便于请求流水完整留痕。
 */
@Getter
public class MmbaInvokeException extends GenericException {

    private final Integer responseCode;
    private final String traceId;
    private final String responseBody;
    private final String rawResult;

    public MmbaInvokeException(String message, Integer responseCode, String traceId, String responseBody, String rawResult) {
        super(message);
        this.responseCode = responseCode;
        this.traceId = traceId;
        this.responseBody = responseBody;
        this.rawResult = rawResult;
    }

    public MmbaInvokeException(String message, Integer responseCode, String traceId, String responseBody, String rawResult, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode;
        this.traceId = traceId;
        this.responseBody = responseBody;
        this.rawResult = rawResult;
    }
}
