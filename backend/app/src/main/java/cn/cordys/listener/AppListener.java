package cn.cordys.listener;

import cn.cordys.common.service.DataInitService;
import cn.cordys.common.uid.impl.DefaultUidGenerator;
import cn.cordys.common.util.HikariCPUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.rsa.RsaKey;
import cn.cordys.common.util.rsa.RsaUtils;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.service.ExportTaskStopService;
import cn.cordys.crm.system.service.ExtScheduleService;
import cn.cordys.crm.system.service.SystemService;
import cn.cordys.tenant.service.TenantMetaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
class AppListener implements ApplicationRunner {
    @Resource
    private DefaultUidGenerator uidGenerator;

    @Resource
    private ExtScheduleService extScheduleService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DataInitService dataInitService;

    @Resource
    private ExportTaskStopService exportTaskStopService;
    @Resource
    private SystemService systemService;
    @Resource
    private TenantMetaService tenantMetaService;

    /**
     * 应用启动后执行的初始化方法。
     * <p>
     * 此方法会依次初始化唯一 ID 生成器、MinIO 配置和 RSA 配置。
     * </p>
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("===== 开始初始化配置 =====");

        // 初始化唯一ID生成器
        uidGenerator.init();

        // 初始化RSA配置
        log.info("初始化RSA配置");
        initializeRsaConfiguration();

        HikariCPUtils.printHikariCPStatus();

        initializeBusinessByTenant();

        log.info("清理表单缓存");
        systemService.clearFormCache();

        log.info("===== 完成初始化配置 =====");
    }

    /**
     * 初始化 RSA 配置。
     * <p>
     * 此方法首先尝试加载现有的 RSA 密钥。如果不存在，则生成新的 RSA 密钥并保存到文件系统。
     * </p>
     */
    private void initializeRsaConfiguration() {
        // 管理中心 Redis key 统一使用 master: 前缀
        String redisKey = "master:rsa:key";
        try {
            // 从 Redis 获取 RSA 密钥
            String rsaStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (StringUtils.isNotBlank(rsaStr)) {
                // 如果 RSA 密钥存在，反序列化并设置密钥
                RsaKey rsaKey = JSON.parseObject(rsaStr, RsaKey.class);
                RsaUtils.setRsaKey(rsaKey);
                return;
            }
        } catch (Exception e) {
            log.error("从 Redis 获取 RSA 配置失败", e);
        }

        try {
            // 如果 Redis 中没有密钥，生成新的 RSA 密钥并保存到 Redis
            RsaKey rsaKey = RsaUtils.getRsaKey();
            stringRedisTemplate.opsForValue().set(redisKey, Objects.requireNonNull(JSON.toJSONString(rsaKey)));
            RsaUtils.setRsaKey(rsaKey);
        } catch (Exception e) {
            log.error("初始化 RSA 配置失败", e);
        }
    }

    private void initializeBusinessByTenant() {
        String previousTenantId = TenantContext.getTenantId();
        List<String> tenantIds = getEnabledTenantIds();
        for (String tenantId : tenantIds) {
            try {
                TenantContext.setTenantId(tenantId);
                log.info("初始化租户业务配置，tenantId={}", tenantId);

                log.info("初始化定时任务，tenantId={}", tenantId);
                extScheduleService.startEnableSchedules();

                log.info("初始化默认组织数据，tenantId={}", tenantId);
                dataInitService.initOneTime();

                log.info("停止导出任务，tenantId={}", tenantId);
                exportTaskStopService.stopPreparedAll();
            } catch (Exception e) {
                log.error("租户初始化失败，tenantId={}", tenantId, e);
            }
        }
        if (StringUtils.isBlank(previousTenantId)) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(previousTenantId);
        }
    }

    private List<String> getEnabledTenantIds() {
        Set<String> enabledTenantIds = tenantMetaService.listEnabledTenantIds();
        List<String> tenantIds = new ArrayList<>();
        if (enabledTenantIds != null) {
            tenantIds.addAll(enabledTenantIds);
        }
        if (!tenantIds.contains(TenantContext.DEFAULT_TENANT_ID)) {
            tenantIds.add(TenantContext.DEFAULT_TENANT_ID);
        }
        return tenantIds;
    }


}