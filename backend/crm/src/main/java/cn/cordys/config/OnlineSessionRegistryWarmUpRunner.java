package cn.cordys.config;

import cn.cordys.security.OnlineSessionRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后从 Redis Session 补写在线注册表。
 */
@Slf4j
@Component
public class OnlineSessionRegistryWarmUpRunner implements ApplicationRunner {

    @Resource
    private OnlineSessionRegistry onlineSessionRegistry;

    @Override
    public void run(ApplicationArguments args) {
        if (onlineSessionRegistry == null) {
            return;
        }
        try {
            onlineSessionRegistry.warmUpFromExistingSessions();
            log.info("在线 Session 注册表启动预热完成");
        } catch (Exception e) {
            log.warn("在线 Session 注册表启动预热失败: {}", e.getMessage());
        }
    }
}
