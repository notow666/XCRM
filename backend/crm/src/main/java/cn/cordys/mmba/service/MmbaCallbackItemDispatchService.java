package cn.cordys.mmba.service;

import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按单条 ZzyData 提交事务，避免整批回调占用同一 JDBC 连接。
 */
@Service
public class MmbaCallbackItemDispatchService {

    @Resource
    @Lazy
    private MmbaCallbackDispatchService mmbaCallbackDispatchService;

    @Transactional(rollbackFor = Exception.class)
    public void dispatchAuditItem(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord, ZzyData data) {
        mmbaCallbackDispatchService.processAuditItem(dto, callbackRecord, data);
    }

    @Transactional(rollbackFor = Exception.class)
    public void dispatchCommandItem(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord, ZzyData data) {
        mmbaCallbackDispatchService.processCommandItem(dto, callbackRecord, data);
    }
}
