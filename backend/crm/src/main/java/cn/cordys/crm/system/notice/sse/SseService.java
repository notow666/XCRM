package cn.cordys.crm.system.notice.sse;

import cn.cordys.common.exception.GenericException;
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
import cn.cordys.crm.system.notice.dto.SseMessageDTO;
import cn.cordys.crm.system.service.SendModuleService;
import jakarta.annotation.Resource;
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
    private final Map<String, Map<String, ClientSinkWrapper>> userClients = new ConcurrentHashMap<>();
    @Resource
    private ExtNotificationMapper extNotificationMapper;
    @Resource
    private SendModuleService sendModuleService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
    public Flux<String> addClient(SsePrincipalKind kind, String tenantId, String userId, String clientId) {
        log.info("SSE addClient kind={} userId={} clientId={}", kind, userId, clientId);

        if (StringUtils.isBlank(clientId)) {
            log.info("Client ID is blank, cannot add client.");
            return null;
        }
        String userKey = connectionKey(kind, tenantId, userId);
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
            return wrapper.flux;
        }
    }

    public void removeClient(SsePrincipalKind kind, String tenantId, String userId, String clientId) {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(userId)) {
            return;
        }
        String userKey = connectionKey(kind, tenantId, userId);
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
        log.info("Broadcast to tenant user {} at {}", userId, System.currentTimeMillis());
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
