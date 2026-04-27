package cn.cordys.mmba.callback;

import cn.cordys.mmba.dto.MmbaAuditRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractZZYConsumer implements ZZYConsumerService{

    public final void mainProcess(MmbaAuditRequest dto) {
        process(dto);
    }

    protected abstract void process(MmbaAuditRequest dto);
}
