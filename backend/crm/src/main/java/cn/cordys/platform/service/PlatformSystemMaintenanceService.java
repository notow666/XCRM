package cn.cordys.platform.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.constants.PlatformSseEventType;
import cn.cordys.common.constants.TopicConstants;
import cn.cordys.common.redis.MessagePublisher;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.pager.Pager;
import cn.cordys.platform.dto.request.PlatformAnnouncementPageRequest;
import cn.cordys.platform.dto.response.PlatformSystemAnnouncementItemResponse;
import cn.cordys.platform.dto.response.PlatformSystemMaintenanceStatusResponse;
import cn.cordys.platform.service.PlatformSessionOverviewService.SessionOnlineSnapshot;
import cn.cordys.crm.system.notice.dto.NoticeRedisMessage;
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.security.OnlineSessionRegistry;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class PlatformSystemMaintenanceService {

    private static final int DEFAULT_GRACE_SECONDS = 60;
    private static final String FORCE_LOGOUT_MESSAGE = "系统维护中，请保存当前工作后登出账号";

    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    @Resource
    private PlatformAdminService platformAdminService;

    @Resource
    private PlatformSessionOverviewService platformSessionOverviewService;

    @Resource
    private PlatformOverviewService platformOverviewService;

    @Resource
    private OnlineSessionRegistry onlineSessionRegistry;

    @Resource
    private MessagePublisher messagePublisher;

    @Resource
    private SseService sseService;

    @Resource(name = ExecutorBeanNames.MAIN_ASYNC)
    private Executor mainAsyncExecutor;

    public boolean isMaintenanceMode() {
        return maintenanceMode.get();
    }

    public PlatformSystemMaintenanceStatusResponse getStatus() {
        SessionOnlineSnapshot snapshot = platformSessionOverviewService.scanOnlineSessions();
        PlatformSystemMaintenanceStatusResponse response = new PlatformSystemMaintenanceStatusResponse();
        response.setMaintenanceMode(maintenanceMode.get());
        response.setOnlineUserTotal(snapshot.getOnlineUserTotal());
        response.setOnlineTenantUserTotal(snapshot.getOnlineTenantUserTotal());
        response.setOnlineDataSpecialistUserCount(snapshot.getOnlineDataSpecialistUserCount());
        return response;
    }

    public Pager<List<PlatformSystemAnnouncementItemResponse>> pageAnnouncements(PlatformAnnouncementPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, Math.min(100, request.getPageSize()));
        int offset = (current - 1) * pageSize;

        Long total = masterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_system_announcement", Long.class);
        String sql = "SELECT id, subject, content, operator_id, create_time FROM platform_system_announcement " +
                "ORDER BY create_time DESC LIMIT ? OFFSET ?";
        List<PlatformSystemAnnouncementItemResponse> list = masterJdbcTemplate.query(sql, (rs, rowNum) -> {
            PlatformSystemAnnouncementItemResponse item = new PlatformSystemAnnouncementItemResponse();
            item.setId(rs.getString("id"));
            item.setSubject(rs.getString("subject"));
            item.setContent(rs.getString("content"));
            item.setOperatorId(rs.getString("operator_id"));
            item.setCreateTime(rs.getLong("create_time"));
            return item;
        }, pageSize, offset);
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public PlatformSystemAnnouncementItemResponse publishAnnouncement(String subject, String content, String operatorId) {
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        masterJdbcTemplate.update(
                "INSERT INTO platform_system_announcement (id, subject, content, operator_id, create_time) VALUES (?, ?, ?, ?, ?)",
                id, subject, content, operatorId, now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", PlatformSseEventType.PLATFORM_SYSTEM_ANNOUNCEMENT);
        payload.put("id", id);
        payload.put("subject", subject);
        payload.put("content", content);
        payload.put("createTime", now);
        publishPlatformBroadcast(PlatformSseEventType.PLATFORM_SYSTEM_ANNOUNCEMENT, JSON.toJSONString(payload));

        platformAdminService.recordAudit(operatorId, "SYSTEM_ANNOUNCE", "", "SUCCESS",
                "subject=" + StringUtils.abbreviate(subject, 200), 0L);

        PlatformSystemAnnouncementItemResponse item = new PlatformSystemAnnouncementItemResponse();
        item.setId(id);
        item.setSubject(subject);
        item.setContent(content);
        item.setOperatorId(operatorId);
        item.setCreateTime(now);
        return item;
    }

    public void forceLogoutAll(String operatorId, Integer graceSeconds) {
        int seconds = graceSeconds == null || graceSeconds <= 0 ? DEFAULT_GRACE_SECONDS : graceSeconds;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", PlatformSseEventType.PLATFORM_FORCE_LOGOUT);
        payload.put("message", FORCE_LOGOUT_MESSAGE);
        payload.put("countdownSeconds", seconds);
        publishPlatformBroadcast(PlatformSseEventType.PLATFORM_FORCE_LOGOUT, JSON.toJSONString(payload));

        int initialTargetCount = collectTenantAndDataSpecialistPrincipals().size();
        platformAdminService.recordAudit(operatorId, "FORCE_LOGOUT_ALL", "", "SUCCESS",
                "graceSeconds=" + seconds + ",initialTargets=" + initialTargetCount, 0L);

        mainAsyncExecutor.execute(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Set<String> principalsToKick = collectTenantAndDataSpecialistPrincipals();
            int targetCount = principalsToKick.size();
            int kickedCount = kickPrincipals(principalsToKick);
            enterMaintenanceMode(operatorId);
            SessionOnlineSnapshot after = onlineSessionRegistry != null
                    ? platformSessionOverviewService.fromStats(onlineSessionRegistry.snapshot())
                    : platformSessionOverviewService.scanOnlineSessions();
            platformOverviewService.invalidateOverviewCache();
            publishForceLogoutDoneEvent(targetCount, kickedCount, after, true);
            log.info("强制全员下线完成，目标 {} 个主体，成功踢出 {} 个，已进入维护排水模式", targetCount, kickedCount);
        });
    }

    public void enterMaintenanceMode(String operatorId) {
        maintenanceMode.set(true);
        platformAdminService.recordAudit(operatorId, "MAINTENANCE_ENTER", "", "SUCCESS", "", 0L);
        log.info("系统已进入维护排水模式，operator={}", operatorId);
    }

    public void exitMaintenanceMode(String operatorId) {
        maintenanceMode.set(false);
        platformAdminService.recordAudit(operatorId, "MAINTENANCE_EXIT", "", "SUCCESS", "", 0L);
        log.info("系统已退出维护排水模式，operator={}", operatorId);
    }

    private void publishForceLogoutDoneEvent(int targetCount, int kickedCount, SessionOnlineSnapshot after,
                                           boolean maintenanceModeActive) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", PlatformSseEventType.PLATFORM_FORCE_LOGOUT_DONE);
        payload.put("targetCount", targetCount);
        payload.put("kickedCount", kickedCount);
        payload.put("failedCount", Math.max(0, targetCount - kickedCount));
        payload.put("maintenanceMode", maintenanceModeActive);
        payload.put("onlineUserTotal", after.getOnlineUserTotal());
        payload.put("onlineTenantUserTotal", after.getOnlineTenantUserTotal());
        payload.put("onlineDataSpecialistUserCount", after.getOnlineDataSpecialistUserCount());
        publishPlatformAdminEvent(PlatformSseEventType.PLATFORM_FORCE_LOGOUT_DONE, JSON.toJSONString(payload));
    }

    private Set<String> collectTenantAndDataSpecialistPrincipals() {
        Set<String> principals = new HashSet<>();
        if (onlineSessionRegistry != null) {
            Set<String> fromRegistry = onlineSessionRegistry.snapshot().getTenantAndDataSpecialistPrincipals();
            if (fromRegistry != null) {
                principals.addAll(fromRegistry);
            }
        }
        SessionOnlineSnapshot snapshot = platformSessionOverviewService.scanFromPrincipalIndex();
        if (snapshot.getOnlineUserTotal() == 0) {
            snapshot = platformSessionOverviewService.scanOnlineSessions();
        }
        snapshot.getLocalPrincipalsByTenant().values().forEach(principals::addAll);
        snapshot.getSessionCountByPrincipal().keySet().stream()
                .filter(p -> p.startsWith(LoginAuthenticateConstants.LoginAuthenticateType.DATA_SPECIALIST.name() + ":"))
                .forEach(principals::add);
        return principals;
    }

    private int kickPrincipals(Set<String> principals) {
        int kicked = 0;
        for (String principal : principals) {
            if (LoginAuthenticateConstants.LoginAuthenticateType.PLATFORM.name().equalsIgnoreCase(
                    principal.split(":", 2)[0])) {
                continue;
            }
            try {
                int deleted = onlineSessionRegistry != null
                        ? onlineSessionRegistry.kickPrincipal(principal)
                        : kickPrincipalLegacy(principal);
                if (deleted > 0) {
                    kicked++;
                }
            } catch (Exception e) {
                log.error("踢出用户失败 principal={}: {}", principal, e.getMessage());
            }
        }
        return kicked;
    }

    private int kickPrincipalLegacy(String principal) {
        String[] parts = principal.split(":", 3);
        if (parts.length < 3) {
            return 0;
        }
        return SessionUtils.kickOutUserAndCount(parts[0], parts[1], parts[2]);
    }

    private void publishPlatformAdminEvent(String noticeType, String payloadJson) {
        sseService.broadcastPlatformAdminEvent(payloadJson);

        NoticeRedisMessage noticeRedisMessage = new NoticeRedisMessage();
        noticeRedisMessage.setNoticeType(noticeType);
        noticeRedisMessage.setBroadcastScope(PlatformSseEventType.BROADCAST_SCOPE_PLATFORM);
        noticeRedisMessage.setMessage(payloadJson);
        messagePublisher.publish(TopicConstants.SSE_TOPIC, JSON.toJSONString(noticeRedisMessage));
    }

    private void publishPlatformBroadcast(String noticeType, String payloadJson) {
        // 本机直推：发布节点上的 SSE 连接不依赖 Redis 订阅回调（DevTools 热重启后 static 消费者可能仍指向旧实例）
        sseService.tryBroadcastPlatformEvent(payloadJson);

        NoticeRedisMessage noticeRedisMessage = new NoticeRedisMessage();
        noticeRedisMessage.setNoticeType(noticeType);
        noticeRedisMessage.setBroadcastScope(PlatformSseEventType.BROADCAST_SCOPE_TENANT_AND_DS);
        noticeRedisMessage.setMessage(payloadJson);
        messagePublisher.publish(TopicConstants.SSE_TOPIC, JSON.toJSONString(noticeRedisMessage));
    }
}
