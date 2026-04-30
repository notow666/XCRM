package cn.cordys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@SpringBootApplication(exclude = {
        QuartzAutoConfiguration.class,
        LdapAutoConfiguration.class,
        Neo4jAutoConfiguration.class
})
@PropertySources({
        @PropertySource(value = "classpath:commons.properties", encoding = "UTF-8", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:commons.properties.${spring.profiles.active:dev}", encoding = "UTF-8", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:cordys-crm.properties", encoding = "UTF-8", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:cordys-crm.properties.${spring.profiles.active:dev}", encoding = "UTF-8", ignoreResourceNotFound = true),
})
@ServletComponentScan
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}


