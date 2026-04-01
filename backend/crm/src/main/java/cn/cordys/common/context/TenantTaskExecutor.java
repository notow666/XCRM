package cn.cordys.common.context;

import cn.cordys.context.TenantContext;
import cn.cordys.tenant.service.TenantMetaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

@Component
@Slf4j
public class TenantTaskExecutor {

    @Resource
    private TenantMetaService tenantMetaService;

    public void runForEachEnabledTenant(String taskName, Consumer<String> tenantTask) {
        String previousTenantId = TenantContext.getTenantId();
        Set<String> tenantIds = new LinkedHashSet<>();
        tenantIds.add(TenantContext.DEFAULT_TENANT_ID);
        tenantIds.addAll(tenantMetaService.listEnabledTenantIds());
        for (String tenantId : tenantIds) {
            try {
                TenantContext.setTenantId(tenantId);
                tenantTask.accept(tenantId);
            } catch (Exception e) {
                log.error("执行多租户定时任务失败，taskName={}, tenantId={}", taskName, tenantId, e);
            }
        }
        if (StringUtils.isBlank(previousTenantId)) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(previousTenantId);
        }
    }
}
