package cn.cordys.common.context;


import cn.cordys.common.constants.MdcConstants;

import cn.cordys.context.TenantContext;

import cn.cordys.tenant.service.TenantMetaService;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.MDC;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 在启用租户列表上执行任务，绑定 {@link TenantContext} 与 MDC tenantId。
 *
 * <p>用于平台级 {@code @QuartzScheduled} 任务需要逐租户访问业务库的场景。</p>
 */

@Component
@Slf4j
public class TenantTaskExecutor {

    @Resource
    private TenantMetaService tenantMetaService;

    public void runForEachEnabledTenant(String taskName, Consumer<String> tenantTask) {

        String previousTenantId = TenantContext.getTenantId();

        String previousMdcTenantId = MDC.get(MdcConstants.TENANT_ID_KEY);

        Set<String> tenantIds = new LinkedHashSet<>();

        tenantIds.add(TenantContext.DEFAULT_TENANT_ID);

        tenantIds.addAll(tenantMetaService.listEnabledTenantIds());

        for (String tenantId : tenantIds) {

            try {

                TenantContext.setTenantId(tenantId);

                MDC.put(MdcConstants.TENANT_ID_KEY, tenantId);

                tenantTask.accept(tenantId);

            } catch (Exception e) {

                log.error("执行多租户定时任务失败，taskName={}, tenantId={}", taskName, tenantId, e);

            } finally {

                MDC.remove(MdcConstants.TENANT_ID_KEY);

            }

        }

        if (StringUtils.isBlank(previousTenantId)) {

            TenantContext.clear();

        } else {

            TenantContext.setTenantId(previousTenantId);

        }

        if (StringUtils.isBlank(previousMdcTenantId)) {

            MDC.remove(MdcConstants.TENANT_ID_KEY);

        } else {

            MDC.put(MdcConstants.TENANT_ID_KEY, previousMdcTenantId);

        }

    }

}


