package io.meterforge.controlplane.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("meterforge")
                .defaultSchema("meterforge")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public static BeanFactoryPostProcessor dependsOnFlywayPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                BeanDefinition bd = beanFactory.getBeanDefinition("entityManagerFactory");
                String[] dependsOn = bd.getDependsOn();
                if (dependsOn == null || dependsOn.length == 0) {
                    bd.setDependsOn("flyway");
                } else {
                    String[] newDependsOn = new String[dependsOn.length + 1];
                    System.arraycopy(dependsOn, 0, newDependsOn, 0, dependsOn.length);
                    newDependsOn[dependsOn.length] = "flyway";
                    bd.setDependsOn(newDependsOn);
                }
            }
        };
    }
}
