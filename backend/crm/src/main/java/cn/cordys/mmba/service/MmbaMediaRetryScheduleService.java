package cn.cordys.mmba.service;

import cn.cordys.common.context.TenantTaskExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mmba.asset.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MmbaMediaRetryScheduleService {

    @Resource
    private TenantTaskExecutor tenantTaskExecutor;
    @Resource
    private MmbaFacadeService mmbaFacadeService;

    @Scheduled(
            initialDelayString = "${mmba.asset.retry.initial-delay-ms:60000}",
            fixedDelayString = "${mmba.asset.retry.fixed-delay-ms:300000}"
    )
    public void retryFailedAssets() {
        tenantTaskExecutor.runForEachEnabledTenant("mmba-media-retry", tenantId -> {
            log.info("MMBA媒体失败补偿开始 tenantId={}", tenantId);
            mmbaFacadeService.retryFailedAssetDownloads();
        });
    }
}
