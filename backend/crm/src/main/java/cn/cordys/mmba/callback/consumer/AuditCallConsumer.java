package cn.cordys.mmba.callback.consumer;

import cn.cordys.common.util.AsyncUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.callback.AbstractZZYConsumer;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AuditCallConsumer extends AbstractZZYConsumer {

    @Override
    public String group() {
        return "GROUP_BY_AUDIT";
    }

    @Override
    public void process(MmbaAuditRequest dto) {
        log.warn("AuditCallConsumer ---> [{}]", JSON.toJSONString(dto));
        try {
            for (ZzyData zzy : dto.getData()) {
                String previousTenantId = TenantContext.getTenantId();
                try {
                    TenantContext.setTenantId(zzy.getTenantId());
                    switch (dto.getBehaviorType()) {

                    }
                } finally {
                    if (StringUtils.isBlank(previousTenantId)) {
                        TenantContext.clear();
                    } else {
                        TenantContext.setTenantId(previousTenantId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("审计回调数据处理异常:{}, ZZYAuditReceiptDto ===> [{}]", e.getMessage(), JSON.toJSONString(dto));
        }
    }
}
