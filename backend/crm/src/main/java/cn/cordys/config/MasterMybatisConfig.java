package cn.cordys.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = {"cn.cordys.platform.mapper", "cn.cordys.tenant.mapper"},
        sqlSessionFactoryRef = "masterSqlSessionFactory"
)
public class MasterMybatisConfig {

    @Bean("masterSqlSessionFactory")
    public SqlSessionFactory masterSqlSessionFactory(@Qualifier("masterDataSource") DataSource masterDataSource,
                                                     ObjectProvider<Interceptor[]> interceptorsProvider,
                                                     MybatisProperties myBatisProperties) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(masterDataSource);
        factoryBean.setTypeAliasesPackage("cn.cordys.platform.domain,cn.cordys.tenant.domain");
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(new org.springframework.core.io.Resource[]{
                resolver.getResource("classpath:cn/cordys/platform/mapper/ExtPlatformUserMapper.xml"),
                resolver.getResource("classpath:cn/cordys/platform/mapper/ExtTenantOpsTaskMapper.xml"),
                resolver.getResource("classpath:cn/cordys/tenant/mapper/ExtTenantMapper.xml"),
                resolver.getResource("classpath:cn/cordys/tenant/mapper/ExtTenantDbConfigMapper.xml")
        });
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

}
