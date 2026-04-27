package cn.cordys.mmba.callback;

import cn.cordys.mmba.dto.MmbaAuditRequest;

public interface ZZYConsumerService {
    String group();
    void mainProcess(MmbaAuditRequest dto);
}
