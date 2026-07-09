package cn.cordys.tenant.service;

import cn.cordys.context.ActiveDbRole;
import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.tenant.constants.TenantDataSourceKeys;
import cn.cordys.tenant.dto.TenantShadowMetaDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 根据租户 ID 与 {@link RoutingContext} 解析动态数据源 lookup key。
 */
@Component
public class TenantDataSourceLookupResolver {

    @Resource
    private TenantShadowMetaService tenantShadowMetaService;

    public String resolveLookupKey(String tenantId) {
        tenantId = StringUtils.trimToNull(tenantId);
        if (tenantId == null) {
            return null;
        }
        RoutingPurpose purpose = RoutingContext.getPurposeOrDefault();
        if (RoutingContext.isPrimaryOnlyPurpose(purpose)) {
            return TenantDataSourceKeys.primary(tenantId);
        }
        if (purpose == RoutingPurpose.SHADOW_FORBIDDEN) {
            return TenantDataSourceKeys.primary(tenantId);
        }
        TenantShadowMetaDTO meta = tenantShadowMetaService.getShadowMeta(tenantId);
        if (purpose == RoutingPurpose.SHADOW_PROVISION
                && meta != null && meta.isShadowEnabled()) {
            return TenantDataSourceKeys.shadow(tenantId);
        }
        if (meta != null && meta.isShadowEnabled() && meta.getActiveDbRole() == ActiveDbRole.SHADOW) {
            return TenantDataSourceKeys.shadow(tenantId);
        }
        return TenantDataSourceKeys.primary(tenantId);
    }
}
