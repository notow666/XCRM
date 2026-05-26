package cn.cordys.mmba.callback;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MMBA Redis Stream 回调消费参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cordys.mmba.callback")
public class MmbaCallbackProperties {

    private int streamParallelConsumers = 5;
    private int batchFlushSize = 16;
    private int dbConcurrency = 8;
}
