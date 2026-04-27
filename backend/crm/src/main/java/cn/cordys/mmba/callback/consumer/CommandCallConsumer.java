package cn.cordys.mmba.callback.consumer;

import cn.cordys.common.util.JSON;
import cn.cordys.mmba.callback.AbstractZZYConsumer;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CommandCallConsumer extends AbstractZZYConsumer {

    @Override
    public String group() {
        return "GROUP_BY_COMMAND";
    }

    @Override
    public void process(MmbaAuditRequest dto) {
        log.warn("CommandCallConsumer ---> [{}]", JSON.toJSONString(dto));
    }
}
