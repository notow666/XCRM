package cn.cordys.config;

import cn.cordys.common.context.RoutingPurposeWebFilter;
import cn.cordys.common.context.TenantContextWebFilter;
import cn.cordys.common.context.TenantSessionConsistencyWebFilter;
import cn.cordys.common.context.TenantShadowForbiddenFilter;
import cn.cordys.common.context.TenantShadowMaintenanceFilter;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.tenant.service.TenantMetaService;
import cn.cordys.tenant.service.TenantShadowMetaService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantContextConfig {

    @Bean
    public FilterRegistrationBean<RoutingPurposeWebFilter> routingPurposeWebFilter() {
        FilterRegistrationBean<RoutingPurposeWebFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RoutingPurposeWebFilter());
        registrationBean.setOrder(-102);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilter(TenantMetaService tenantMetaService,
                                                                                 TenantShadowMetaService tenantShadowMetaService) {
        FilterRegistrationBean<TenantContextWebFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TenantContextWebFilter(tenantMetaService, tenantShadowMetaService));
        registrationBean.setOrder(-101);
        return registrationBean;
    }

    /** 在 TenantContext 写入后以会话租户覆盖（不信任客户端 Header/Query） */
    @Bean
    public FilterRegistrationBean<TenantSessionConsistencyWebFilter> tenantSessionConsistencyWebFilter(ExtDataSpecialistMapper extDataSpecialistMapper) {
        FilterRegistrationBean<TenantSessionConsistencyWebFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TenantSessionConsistencyWebFilter(extDataSpecialistMapper));
        registrationBean.setOrder(-100);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TenantShadowMaintenanceFilter> tenantShadowMaintenanceFilter(
            TenantShadowMetaService tenantShadowMetaService) {
        FilterRegistrationBean<TenantShadowMaintenanceFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TenantShadowMaintenanceFilter(tenantShadowMetaService));
        registrationBean.setOrder(-99);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TenantShadowForbiddenFilter> tenantShadowForbiddenFilter(
            TenantShadowMetaService tenantShadowMetaService) {
        FilterRegistrationBean<TenantShadowForbiddenFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TenantShadowForbiddenFilter(tenantShadowMetaService));
        registrationBean.setOrder(-98);
        return registrationBean;
    }
}

