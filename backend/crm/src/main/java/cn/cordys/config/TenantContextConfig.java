package cn.cordys.config;

import cn.cordys.common.context.TenantContextWebFilter;
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
        registrationBean.setOrder(-110);
        return registrationBean;
    }
}

