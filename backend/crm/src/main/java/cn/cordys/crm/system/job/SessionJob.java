package cn.cordys.crm.system.job;

import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.system.service.SystemService;
import cn.cordys.platform.service.PlatformSessionOverviewService;
import cn.cordys.quartz.anno.QuartzScheduled;
import cn.cordys.security.OnlineSessionRegistry;
import cn.cordys.security.OnlineSessionStats;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SessionJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    @Resource
    private SystemService systemService;

    @Resource
    private TenantTaskExecutor tenantTaskExecutor;

    @Resource
    private PlatformSessionOverviewService platformSessionOverviewService;

    @Resource
    private OnlineSessionRegistry onlineSessionRegistry;

    /**
     * 定时清理没有绑定用户的会话。
     * <p>
     * 该方法每晚 0 点 2 分执行，扫描 Redis 中的会话数据，删除没有绑定用户信息的会话。
     * 此外，还会处理一些特殊情况，如 Redisson 设置了过期时间为 -1 时，手动设置过期时间。
     * </p>
     *
     * <p>
     * 平台级任务：Spring Session 使用全局 Redis key（扫描全部会话）；表单缓存仅清理 ACTIVE 租户
     * （{@link TenantTaskExecutor} 不含 FROZEN 租户）。
     * </p>
     * <p>
     * spring.session.timeout=30d
     * server.servlet.session.timeout=30d
     */
    @QuartzScheduled(cron = "0 2 0 * * ?")
    public void cleanSession() {
        Map<String, Long> userCount = new HashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match("spring:session:sessions:*").count(1000).build();

        try (Cursor<String> scan = stringRedisTemplate.scan(options)) {
            while (scan.hasNext()) {
                String key = scan.next();
                if (key.contains("spring:session:sessions:expires:")) {
                    continue;
                }

                String sessionId = key.substring(key.lastIndexOf(":") + 1);
                Boolean exists = stringRedisTemplate.opsForHash().hasKey(key, "sessionAttr:user");

                // 删除没有绑定用户的会话
                if (!exists) {
                    deleteOrphanSession(sessionId, key);
                } else {
                    // 获取用户信息并检查会话过期时间
                    Object user = redisIndexedSessionRepository.getSessionRedisOperations().opsForHash().get(key, "sessionAttr:user");
                    Long expire = redisIndexedSessionRepository.getSessionRedisOperations().getExpire(key);

                    assert user != null;
                    String userId = (String) MethodUtils.invokeMethod(user, "getId");
                    userCount.merge(userId, 1L, Long::sum);

                    // 记录日志并检查会话的过期时间
                    log.debug("{} : {} 过期时间: {}", key, userId, expire);

                    // 如果过期时间为 -1，则手动设置过期时间为 30 秒
                    if (expire != null && expire == -1) {
                        redisIndexedSessionRepository.getSessionRedisOperations().expire(key, Duration.of(30, ChronoUnit.SECONDS));
                    }
                }
            }
            tenantTaskExecutor.runForEachEnabledTenant("SessionJob.clearFormCache",
                    tenantId -> systemService.clearFormCache());
            reconcileOnlineSessionRegistry();
            log.debug("用户会话统计: {}", JSON.toJSONString(userCount));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 删除未绑定用户的 Session。部分 Redis 残留 hash 缺少 Spring Session 必填字段（如 creationTime），
     * 此时 {@link RedisIndexedSessionRepository#deleteById} 会抛错，需降级为直接删 key。
     */
    private void deleteOrphanSession(String sessionId, String sessionKey) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForHash().hasKey(sessionKey, "creationTime"))) {
            purgeSessionKeys(sessionId);
            return;
        }
        try {
            redisIndexedSessionRepository.deleteById(sessionId);
        } catch (Exception e) {
            log.warn("Spring Session deleteById 失败 sessionId={}，改用 Redis 直删: {}", sessionId, e.getMessage());
            purgeSessionKeys(sessionId);
        }
    }

    private void purgeSessionKeys(String sessionId) {
        stringRedisTemplate.delete("spring:session:sessions:" + sessionId);
        stringRedisTemplate.delete("spring:session:sessions:expires:" + sessionId);
    }

    private void reconcileOnlineSessionRegistry() {
        if (onlineSessionRegistry == null || platformSessionOverviewService == null) {
            return;
        }
        try {
            OnlineSessionStats scanned = platformSessionOverviewService.toOnlineSessionStats(
                    platformSessionOverviewService.scanOnlineSessionsLegacy());
            onlineSessionRegistry.reconcile(scanned);
        } catch (Exception e) {
            log.warn("在线 Session 注册表校准失败: {}", e.getMessage());
        }
    }
}
