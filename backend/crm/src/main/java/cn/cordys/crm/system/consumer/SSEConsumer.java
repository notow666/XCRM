package cn.cordys.crm.system.consumer;

import cn.cordys.common.constants.PlatformSseEventType;
import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.common.constants.TopicConstants;
import cn.cordys.common.redis.TopicConsumer;
import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.customer.constants.CustomerBatchConstants;
import cn.cordys.crm.customer.constants.PoolCustomerBatchConstants;
import cn.cordys.crm.system.notice.dto.NoticeRedisMessage;
import cn.cordys.crm.system.notice.sse.SseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        if (StringUtils.isNotBlank(noticeRedisMessage.getBroadcastScope())) {
            if (PlatformSseEventType.BROADCAST_SCOPE_PLATFORM.equals(noticeRedisMessage.getBroadcastScope())) {
                sseService.broadcastPlatformAdminEvent(noticeRedisMessage.getMessage());
            } else {
                sseService.tryBroadcastPlatformEvent(noticeRedisMessage.getMessage());
            }
            return;
        }
        String tenantId = StringUtils.trimToNull(noticeRedisMessage.getTenantId());
        if (tenantId == null) {
            log.warn("SSE 消息缺少 tenantId，已跳过，避免误用默认租户");
            return;
        }
        TenantContext.setTenantId(tenantId);
        try {
            if (isTenantUserCustomEvent(noticeRedisMessage.getNoticeType())) {
                dispatchTenantUserCustomEvent(tenantId, noticeRedisMessage.getMessage());
                return;
            }
            sseService.broadcastPeriodically(noticeRedisMessage.getMessage(), noticeRedisMessage.getNoticeType());
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isTenantUserCustomEvent(String noticeType) {
        return CustomerBatchConstants.SSE_CUSTOMER_BATCH_BY_CONDITION_DONE.equals(noticeType)
                || PoolCustomerBatchConstants.SSE_POOL_BATCH_BY_CONDITION_DONE.equals(noticeType);
    }

    private void dispatchTenantUserCustomEvent(String tenantId, String envelopeJson) {
        if (StringUtils.isBlank(envelopeJson)) {
            return;
        }
        Map<?, ?> envelope = JSON.parseMap(envelopeJson);
        if (envelope == null || envelope.isEmpty()) {
            return;
        }
        Object userIdObj = envelope.get("userId");
        Object payload = envelope.get("payload");
        if (!(userIdObj instanceof String userId) || StringUtils.isBlank(userId) || payload == null) {
            return;
        }
        sseService.sendToPrincipal(SsePrincipalKind.TENANT, tenantId, userId, payload);
    }
}
