package cn.cordys.crm.system.consumer;

import cn.cordys.common.constants.TopicConstants;
import cn.cordys.common.redis.TopicConsumer;
import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.notice.dto.NoticeRedisMessage;
import cn.cordys.crm.system.notice.sse.SseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SSEConsumer implements TopicConsumer {

    @Resource
    private SseService sseService;

    @Override
    public String getChannel() {
        return TopicConstants.SSE_TOPIC;
    }

    @Override
    public void consume(String message) {
        NoticeRedisMessage noticeRedisMessage = JSON.parseObject(message, NoticeRedisMessage.class);
        String tenantId = StringUtils.trimToNull(noticeRedisMessage.getTenantId());
        if (tenantId == null) {
            log.warn("SSE 消息缺少 tenantId，已跳过，避免误用默认租户");
            return;
        }
        TenantContext.setTenantId(tenantId);
        try {
            sseService.broadcastPeriodically(noticeRedisMessage.getMessage(), noticeRedisMessage.getNoticeType());
        } finally {
            TenantContext.clear();
        }
    }
}
