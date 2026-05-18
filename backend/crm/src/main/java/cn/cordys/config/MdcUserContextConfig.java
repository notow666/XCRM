package cn.cordys.config;

import cn.cordys.common.context.MdcUserContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MdcUserContextConfig {

    /**
     * 晚于 Shiro（-105）、TenantContext（-101）、OrganizationContext（-100），保证会话与 Shiro 内部链已就绪后再写 MDC。
     */
    private static final int MDC_USER_FILTER_ORDER = -90;

    @Bean
    public FilterRegistrationBean<MdcUserContextFilter> mdcUserContextFilter() {
        FilterRegistrationBean<MdcUserContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcUserContextFilter());
        registration.setOrder(MDC_USER_FILTER_ORDER);
        return registration;
    }
}
