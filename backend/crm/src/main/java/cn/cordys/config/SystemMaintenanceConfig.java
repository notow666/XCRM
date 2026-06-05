package cn.cordys.config;

import cn.cordys.common.context.SystemMaintenanceGateFilter;
import cn.cordys.platform.service.PlatformSystemMaintenanceService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemMaintenanceConfig {

    @Bean
    public FilterRegistrationBean<SystemMaintenanceGateFilter> systemMaintenanceGateFilter(
            PlatformSystemMaintenanceService platformSystemMaintenanceService) {
        FilterRegistrationBean<SystemMaintenanceGateFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SystemMaintenanceGateFilter(platformSystemMaintenanceService));
        registrationBean.setOrder(-106);
        return registrationBean;
    }
}
