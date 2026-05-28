package cn.cordys.config;

import cn.cordys.common.context.TenantContextWebFilter;
import cn.cordys.common.context.TenantSessionConsistencyWebFilter;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.tenant.service.TenantMetaService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantContextConfig {

    @Bean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilter(TenantMetaService tenantMetaService) {
        FilterRegistrationBean<TenantContextWebFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TenantContextWebFilter(tenantMetaService));
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
}

