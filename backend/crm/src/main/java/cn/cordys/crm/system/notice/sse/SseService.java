package cn.cordys.crm.system.notice.sse;

import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.common.constants.TopicConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.redis.MessagePublisher;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.redis.TenantRedisKeyBuilder;
import cn.cordys.context.OrganizationContext;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.domain.Notification;
import cn.cordys.crm.system.dto.response.NotificationDTO;
import cn.cordys.crm.system.mapper.ExtNotificationMapper;
import cn.cordys.crm.system.notice.dto.NoticeRedisMessage;
import cn.cordys.crm.system.notice.dto.SseMessageDTO;
import cn.cordys.common.context.TenantSessionBindingSupport;
import cn.cordys.crm.system.service.SendModuleService;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseService {

    private static final String USER_ANNOUNCE_PREFIX = "announce_user:";
    private static final String ANNOUNCE_PREFIX = "announce_content:";
    private static final String USER_PREFIX = "msg_user:";
    private static final String MSG_PREFIX = "msg_content:";
    private static final String USER_READ_PREFIX = "user_read:";
    private static final long PLATFORM_BROADCAST_DEDUPE_MS = 5_000L;
    private final Map<String, Map<String, ClientSinkWrapper>> userClients = new ConcurrentHashMap<>();
    private final Map<String, Long> recentPlatformBroadcastAt = new ConcurrentHashMap<>();
    private volatile long lastPlatformBroadcastCleanupAt;
    @Resource
    private ExtNotificationMapper extNotificationMapper;
    @Resource
    private SendModuleService sendModuleService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;
    @Resource
    private MessagePublisher messagePublisher;

    private String tenantRedisKey(String rawKey) {
        return TenantRedisKeyBuilder.tenantKey(rawKey);
    }

    /**
     * 与 {@link #addClient(SsePrincipalKind, String, String, String)} 使用的连接分组键一致。
     */
    public String connectionKey(SsePrincipalKind kind, String tenantId, String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "userId 不能为空");
        }
        String uid = userId.trim();
        return switch (kind) {
            case TENANT -> {
                String tid = StringUtils.trimToNull(tenantId);
                if (tid == null) {
                    tid = StringUtils.trimToNull(TenantContext.getTenantId());
                }
                if (tid == null) {
                    throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "缺少租户标识 tenantId");
                }
                yield "TENANT:" + tid + ":" + uid;
            }
            case PLATFORM -> "PLATFORM:" + uid;
            case DATA_SPECIALIST -> "DATA_SPECIALIST:" + uid;
        };
    }

    /**
     * 添加或获取现有客户端流（租户内 / 平台 / 数据专员各自独立连接键，与租户切换无关）。
     */
    public Flux<String> addClient(
            SsePrincipalKind kind, String tenantId, String userId, String clientId, HttpServletRequest request) {
        log.info("SSE addClient kind={} userId={} clientId={}", kind, userId, clientId);

        String effectiveTenantId = tenantId;
        if (kind == SsePrincipalKind.TENANT) {
            SessionUser sessionUser = SessionUtils.getUser(request);
            effectiveTenantId = TenantSessionBindingSupport.resolveSseTenantId(sessionUser, tenantId);
            if (!TenantSessionBindingSupport.validateTenantSseSubscription(
                    sessionUser, tenantId, userId, extDataSpecialistMapper)) {
                log.warn("SSE subscribe denied: kind={} requestTenantId={} sessionPresent={} sessionTenantId={} userId={}",
                        kind, tenantId, sessionUser != null, sessionUser != null ? sessionUser.getTenantId() : null, userId);
                return null;
            }
        }

        if (StringUtils.isBlank(clientId)) {
            log.info("Client ID is blank, cannot add client.");
            return null;
        }
        String userKey = connectionKey(kind, effectiveTenantId, userId);
        Map<String, ClientSinkWrapper> inner = userClients.computeIfAbsent(userKey,
                k -> Collections.synchronizedMap(new LinkedHashMap<>()));

        synchronized (inner) {
            if (inner.containsKey(clientId)) {
                return inner.get(clientId).flux;
            }
            ClientSinkWrapper wrapper = new ClientSinkWrapper();
            inner.put(clientId, wrapper);
            if (inner.size() > 2) {
                Iterator<String> it = inner.keySet().iterator();
                String oldest = it.next();
                ClientSinkWrapper old = inner.remove(oldest);
                old.complete();
            }
            wrapper.emit("HEARTBEAT: " + System.currentTimeMillis());
            log.info("SSE client registered: kind={} userId={} clientId={} tenantId={}",
                    kind, userId, clientId, effectiveTenantId);
            return wrapper.flux;
        }
    }

    public void removeClient(
            SsePrincipalKind kind, String tenantId, String userId, String clientId, HttpServletRequest request) {
        String effectiveTenantId = tenantId;
        if (kind == SsePrincipalKind.TENANT) {
            SessionUser sessionUser = SessionUtils.getUser(request);
            effectiveTenantId = TenantSessionBindingSupport.resolveSseTenantId(sessionUser, tenantId);
            if (!TenantSessionBindingSupport.validateTenantSseSubscription(
                    sessionUser, tenantId, userId, extDataSpecialistMapper)) {
                return;
            }
        }
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(userId)) {
            return;
        }
        String userKey = connectionKey(kind, effectiveTenantId, userId);
        Map<String, ClientSinkWrapper> map = userClients.get(userKey);
        if (map == null) {
            return;
        }
        synchronized (map) {
            ClientSinkWrapper w = map.remove(clientId);
            if (w != null) {
                w.complete();
            }
            if (map.isEmpty()) {
                userClients.remove(userKey);
            }
        }
    }

    /**
     * 向指定主体下所有在线客户端推送一条 JSON 文本帧。
     */
    public void sendToPrincipal(SsePrincipalKind kind, String tenantId, String userId, Object data) {
        String userKey = connectionKey(kind, tenantId, userId);
        Map<String, ClientSinkWrapper> map = userClients.get(userKey);
        if (map != null) {
            map.forEach((clientId, wrapper) -> wrapper.emit(JSON.toJSONString(data)));
        }
    }

    /**
     * 向租户内指定用户推送自定义 JSON 事件：本机直推 + Redis 广播，避免异步任务节点与 SSE 连接节点不一致时丢消息。
     */
    public void publishTenantUserCustomEvent(String tenantId, String userId, String noticeType, Object payload) {
        if (StringUtils.isAnyBlank(tenantId, userId, noticeType) || payload == null) {
            return;
        }
        sendToPrincipal(SsePrincipalKind.TENANT, tenantId, userId, payload);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("userId", userId);
        envelope.put("payload", payload);
        NoticeRedisMessage noticeRedisMessage = new NoticeRedisMessage();
        noticeRedisMessage.setTenantId(tenantId);
        noticeRedisMessage.setNoticeType(noticeType);
        noticeRedisMessage.setMessage(JSON.toJSONString(envelope));
        messagePublisher.publish(TopicConstants.SSE_TOPIC, JSON.toJSONString(noticeRedisMessage));
    }

    /**
     * 平台级广播：向所有租户侧与数据专员 SSE 连接推送 JSON 文本帧。
     * 短窗口内相同 payload 仅推送一次，避免本机直推与 Redis 订阅重复投递。
     *
     * @return 是否实际执行了推送（false 表示短窗口内重复消息已跳过）
     */
    public boolean tryBroadcastPlatformEvent(String payloadJson) {
        if (StringUtils.isBlank(payloadJson)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String dedupeKey = Integer.toHexString(payloadJson.hashCode());
        Long lastAt = recentPlatformBroadcastAt.put(dedupeKey, now);
        if (lastAt != null && now - lastAt < PLATFORM_BROADCAST_DEDUPE_MS) {
            return false;
        }
        if (now - lastPlatformBroadcastCleanupAt > PLATFORM_BROADCAST_DEDUPE_MS) {
            lastPlatformBroadcastCleanupAt = now;
            recentPlatformBroadcastAt.entrySet().removeIf(e -> now - e.getValue() > PLATFORM_BROADCAST_DEDUPE_MS);
        }
        broadcastPlatformEvent(payloadJson);
        return true;
    }

    /**
     * 平台级广播：向所有租户侧与数据专员 SSE 连接推送 JSON 文本帧。
     */
    public void broadcastPlatformEvent(String payloadJson) {
        if (StringUtils.isBlank(payloadJson)) {
            return;
        }
        int tenantGroups = 0;
        int dataSpecialistGroups = 0;
        int clientCount = 0;
        for (Map.Entry<String, Map<String, ClientSinkWrapper>> entry : userClients.entrySet()) {
            String userKey = entry.getKey();
            if (userKey == null) {
                continue;
            }
            if (!userKey.startsWith("TENANT:") && !userKey.startsWith("DATA_SPECIALIST:")) {
                continue;
            }
            if (userKey.startsWith("TENANT:")) {
                tenantGroups++;
            } else {
                dataSpecialistGroups++;
            }
            Map<String, ClientSinkWrapper> clients = entry.getValue();
            if (clients == null || clients.isEmpty()) {
                continue;
            }
            clientCount += clients.size();
            clients.forEach((clientId, wrapper) -> wrapper.emit(payloadJson));
        }
        log.info("平台广播 SSE 已推送：租户连接分组 {}、数据专员连接分组 {}、客户端 {}（总注册分组 {}）",
                tenantGroups, dataSpecialistGroups, clientCount, userClients.size());
    }

    /**
     * 向租户内所有在线 SSE 连接推送 JSON 文本帧。
     */
    public void broadcastTenantEvent(String tenantId, String payloadJson) {
        if (StringUtils.isAnyBlank(tenantId, payloadJson)) {
            return;
        }
        String prefix = "TENANT:" + tenantId.trim() + ":";
        int groups = 0;
        int clientCount = 0;
        for (Map.Entry<String, Map<String, ClientSinkWrapper>> entry : userClients.entrySet()) {
            String userKey = entry.getKey();
            if (userKey == null || !userKey.startsWith(prefix)) {
                continue;
            }
            groups++;
            Map<String, ClientSinkWrapper> clients = entry.getValue();
            if (clients == null || clients.isEmpty()) {
                continue;
            }
            clientCount += clients.size();
            clients.forEach((clientId, wrapper) -> wrapper.emit(payloadJson));
        }
        log.info("租户 SSE 广播已推送：tenantId={}, 连接分组={}, 客户端={}", tenantId, groups, clientCount);
    }

    /**
     * 向所有管理中心平台管理员 SSE 连接推送 JSON 文本帧。
     */
    public void broadcastPlatformAdminEvent(String payloadJson) {
        if (StringUtils.isBlank(payloadJson)) {
            return;
        }
        int platformGroups = 0;
        int clientCount = 0;
        for (Map.Entry<String, Map<String, ClientSinkWrapper>> entry : userClients.entrySet()) {
            String userKey = entry.getKey();
            if (userKey == null || !userKey.startsWith("PLATFORM:")) {
                continue;
            }
            platformGroups++;
            Map<String, ClientSinkWrapper> clients = entry.getValue();
            if (clients == null || clients.isEmpty()) {
                continue;
            }
            clientCount += clients.size();
            clients.forEach((clientId, wrapper) -> wrapper.emit(payloadJson));
        }
        log.info("平台管理 SSE 已推送：连接分组 {}、客户端 {}（总注册分组 {}）",
                platformGroups, clientCount, userClients.size());
    }

    public void sendToClient(SsePrincipalKind kind, String tenantId, String userId, String clientId, Object data) {
        Optional.ofNullable(userClients.get(connectionKey(kind, tenantId, userId)))
                .map(m -> m.get(clientId)).ifPresent(wrapper -> wrapper.emit(JSON.toJSONString(data)));
    }

    /**
     * 定时广播逻辑调用（仅租户内通知：依赖当前线程 {@link TenantContext} 与 Redis 租户键）。
     */
    public void broadcastPeriodically(String userId, String sendType) {
        SseMessageDTO msg = buildMessage(userId, sendType);
        String tenantId = TenantContext.requireTenantId();
        sendToPrincipal(SsePrincipalKind.TENANT, tenantId, userId, msg);
        log.debug("Broadcast to tenant user {} at {}", userId, System.currentTimeMillis());
    }

    private SseMessageDTO buildMessage(String userId, String sendType) {
        SseMessageDTO dto = new SseMessageDTO();
        if (Strings.CI.equals(sendType, NotificationConstants.Type.SYSTEM_NOTICE.toString())) {
            List<String> modules = sendModuleService.getNoticeModules();
            Set<String> sysValues = stringRedisTemplate.opsForZSet().range(tenantRedisKey(USER_PREFIX + userId), 0, -1);
            if (CollectionUtils.isNotEmpty(sysValues)) {
                dto.setNotificationDTOList(buildDTOList(sysValues, MSG_PREFIX));
            } else {
                if (CollectionUtils.isNotEmpty(modules)) {
                    List<NotificationDTO> list = extNotificationMapper
                            .selectLastList(userId, OrganizationContext.getOrganizationId(), modules);
                    list.forEach(n -> n.setContentText(new String(n.getContent())));
                    dto.setNotificationDTOList(list);
                }
            }
        }
        if (Strings.CI.equals(sendType, NotificationConstants.Type.ANNOUNCEMENT_NOTICE.toString())) {
            Set<String> values = stringRedisTemplate.opsForZSet().range(tenantRedisKey(USER_ANNOUNCE_PREFIX + userId), 0, -1);
            if (CollectionUtils.isNotEmpty(values)) {
                dto.setAnnouncementDTOList(buildDTOList(values, ANNOUNCE_PREFIX));
            }
        }
        dto.setRead(Boolean.parseBoolean(
                stringRedisTemplate.opsForValue().get(tenantRedisKey(USER_READ_PREFIX + userId))
        ));
        return dto;
    }

    private List<NotificationDTO> buildDTOList(Set<String> values, String prefix) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(val -> stringRedisTemplate.opsForValue().get(tenantRedisKey(prefix + val)))
                .filter(StringUtils::isNotBlank)
                .map(json -> {
                    Notification notification = JSON.parseObject(json, Notification.class);
                    NotificationDTO dto = new NotificationDTO();
                    BeanUtils.copyBean(dto, notification);
                    dto.setContentText(new String(notification.getContent()));
                    return dto;
                })
                .sorted(Comparator.comparing(NotificationDTO::getCreateTime).reversed())
                .toList();
    }

    private static class ClientSinkWrapper {
        private final Sinks.Many<String> sink;
        private final Flux<String> flux;

        ClientSinkWrapper() {
            this.sink = Sinks.many().multicast().onBackpressureBuffer();
            this.flux = sink.asFlux()
                    .mergeWith(Flux.interval(Duration.ofSeconds(15))
                            .map(tick -> "HEARTBEAT: " + System.currentTimeMillis()))
                    .doOnCancel(this::complete);
        }

        void emit(String message) {
            sink.tryEmitNext(message);
        }

        void complete() {
            sink.tryEmitComplete();
        }
    }
}
