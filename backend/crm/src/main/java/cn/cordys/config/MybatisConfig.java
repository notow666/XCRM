package cn.cordys.config;

import cn.cordys.common.handler.ListTypeHandler;
import cn.cordys.common.interceptor.UserDesensitizationInterceptor;
import cn.cordys.mybatis.interceptor.MybatisInterceptor;
import cn.cordys.quartz.anno.QuartzDataSource;
import com.github.pagehelper.PageInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 配置类，用于配置 MyBatis 和数据源相关的设置。
 * <p>
 * 本类负责配置 MyBatis 的分页拦截器、用户信息脱敏拦截器、以及数据库源的配置，
 * 其中包括主数据源与 Quartz 相关的数据源配置。
 * </p>
 *
 * @version 1.0
 */
@Configuration
@MapperScan(basePackages = {"cn.cordys.crm.**.mapper", "cn.cordys.common.mapper"}, sqlSessionFactoryRef = "sqlSessionFactory")
@EnableTransactionManagement
public class MybatisConfig {

    private static final String DEFAULT_TENANT_ID = "default";

    /**
     * 供运行时动态注册租户数据源使用。
     */
    @Bean
    public DynamicTenantRoutingDataSource tenantRoutingDataSource(@Qualifier("dataSourceProperties") DataSourceProperties properties) {
        DataSource defaultDataSource = buildDataSource(
                properties.determineDriverClassName(),
                properties.determineUrl(),
                properties.determineUsername(),
                properties.determinePassword(),
                "CordysTenantDefaultHikariCP"
        );

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DEFAULT_TENANT_ID, defaultDataSource);

        DynamicTenantRoutingDataSource routingDataSource = new DynamicTenantRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(Objects.requireNonNull(defaultDataSource));
        routingDataSource.initTargets(targetDataSources);
        return routingDataSource;
    }

    /**
     * 主业务数据源（按租户路由）。
     */
    @Bean
    @Primary
    public DataSource dataSource(DynamicTenantRoutingDataSource tenantRoutingDataSource) {
        return tenantRoutingDataSource;
    }

    @Bean("sqlSessionFactory")
    @Primary
    public SqlSessionFactory sqlSessionFactory(@Qualifier("dataSource") DataSource dataSource,
                                               ObjectProvider<Interceptor[]> interceptorsProvider,
                                               MybatisProperties myBatisProperties) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] crmMapperResources = resolver.getResources("classpath*:cn/cordys/crm/**/mapper/*.xml");
        org.springframework.core.io.Resource[] commonMapperResources = resolver.getResources("classpath*:cn/cordys/common/mapper/*.xml");
        org.springframework.core.io.Resource[] allMapperResources =
                java.util.stream.Stream.concat(java.util.Arrays.stream(crmMapperResources), java.util.Arrays.stream(commonMapperResources))
                        .toArray(org.springframework.core.io.Resource[]::new);
        factoryBean.setMapperLocations(allMapperResources);
        factoryBean.setPlugins(interceptorsProvider.getIfAvailable());

        MybatisProperties.CoreConfiguration coreConfiguration = myBatisProperties.getConfiguration();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setCacheEnabled(coreConfiguration.getCacheEnabled());
        configuration.setLazyLoadingEnabled(coreConfiguration.getLazyLoadingEnabled());
        configuration.setAggressiveLazyLoading(coreConfiguration.getAggressiveLazyLoading());
        configuration.setUseColumnLabel(coreConfiguration.getUseColumnLabel());
        configuration.setAutoMappingBehavior(coreConfiguration.getAutoMappingBehavior());
        configuration.setDefaultStatementTimeout(coreConfiguration.getDefaultStatementTimeout());
        configuration.setMapUnderscoreToCamelCase(coreConfiguration.getMapUnderscoreToCamelCase());
        configuration.setLogImpl(coreConfiguration.getLogImpl());
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }

    @Bean("sqlSessionTemplate")
    @Primary
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 配置 MyBatis 的分页拦截器。
     * <p>
     * 该方法创建并返回一个 {@link PageInterceptor} 实例，用于启用 MyBatis 分页功能。
     * </p>
     *
     * @return 配置好的分页拦截器实例
     */
    @Bean
    public PageInterceptor pageInterceptor() {
        PageInterceptor pageInterceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");
        properties.setProperty("rowBoundsWithCount", "true");
        properties.setProperty("reasonable", "true");
        properties.setProperty("offsetAsPageNum", "true");
        properties.setProperty("pageSizeZero", "true");
        pageInterceptor.setProperties(properties);
        return pageInterceptor;
    }

    /**
     * 配置 MyBatis 的自定义拦截器。
     * <p>
     * 该方法创建并返回一个 {@link MybatisInterceptor} 实例，将多个拦截器配置合并到一个列表中。
     * </p>
     *
     * @param interceptorConfigs 配置的拦截器列表
     *
     * @return 配置好的自定义拦截器实例
     */
    @Bean
    public MybatisInterceptor dbInterceptor(List<MybatisInterceptorConfig>[] interceptorConfigs) {
        List<MybatisInterceptorConfig> mybatisInterceptorConfigs = new ArrayList<>();
        for (List<MybatisInterceptorConfig> configList : interceptorConfigs) {
            mybatisInterceptorConfigs.addAll(configList);
        }
        // 统一配置
        MybatisInterceptor interceptor = new MybatisInterceptor();
        interceptor.setInterceptorConfigList(mybatisInterceptorConfigs);
        return interceptor;
    }

    /**
     * 配置用户信息脱敏的拦截器。
     * <p>
     * 该方法创建并返回一个 {@link UserDesensitizationInterceptor} 实例，
     * 用于对用户信息进行脱敏处理。
     * </p>
     *
     * @return 配置好的用户脱敏拦截器实例
     */
    @Bean
    public UserDesensitizationInterceptor userDesensitizationInterceptor() {
        return new UserDesensitizationInterceptor();
    }

    private DataSource buildDataSource(String driverClassName,
                                       String url,
                                       String username,
                                       String password,
                                       String poolName) {
        HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setPoolName(poolName);
        return dataSource;
    }

    /**
     * 配置 Quartz 数据源。
     * <p>
     * 该方法根据配置文件中的属性创建一个 Quartz 数据源，使用 {@link HikariDataSource} 作为数据源类型。
     * </p>
     *
     * @param properties Quartz 数据源的基础配置
     *
     * @return 配置好的 Quartz 数据源实例
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.quartz.hikari")
    @QuartzDataSource
    public DataSource quartzDataSource(@Qualifier("quartzDataSourceProperties") DataSourceProperties properties) {
        return DataSourceBuilder.create(properties.getClassLoader()).type(HikariDataSource.class)
                .driverClassName(properties.determineDriverClassName())
                .url(properties.determineUrl())
                .username(properties.determineUsername())
                .password(properties.determinePassword())
                .build();
    }

    /**
     * 配置主数据源的基础属性。
     * <p>
     * 该方法创建并返回一个 {@link DataSourceProperties} 实例，
     * 用于配置 Spring Boot 中的数据源属性。
     * </p>
     *
     * @return 配置好的数据源属性实例
     */
    @Bean("dataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 配置 Quartz 数据源的基础属性。
     * <p>
     * 该方法创建并返回一个 {@link DataSourceProperties} 实例，
     * 用于配置 Quartz 数据源的相关属性。
     * </p>
     *
     * @return 配置好的 Quartz 数据源属性实例
     */
    @Bean("quartzDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.quartz")
    public DataSourceProperties quartzDataSourceProperties() {
        return new DataSourceProperties();
    }


    /**
     * baseMapper 映射 tags 字段
     *
     * @return
     */
    @Bean
    public ListTypeHandler listTypeHandler() {
        return new ListTypeHandler();
    }

}
