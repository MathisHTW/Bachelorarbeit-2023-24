package de.bachelorarbeit.MicroserviceA;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "barEntityManagerFactory",
        transactionManagerRef = "barTransactionManager",
        basePackages = { "de.bachelorarbeit.MicroserviceA.DB2.repositories" }
)
public class DB2Config {

    @Value("${spring.db2.jpa.hibernate.ddl-auto}")
    private String DDL_AUTO_DB2;

    @Bean(name = "barDataSource")
    @ConfigurationProperties(prefix = "spring.db2.datasource")
    public DataSource dataSource() {
        HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class).build();
        dataSource.setConnectionTimeout(250);
        dataSource.setMaxLifetime(250);
        dataSource.setValidationTimeout(250);
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }

    @Bean(name = "barEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean barEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("barDataSource") DataSource dataSource
    ) {

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("hibernate.hbm2ddl.auto", DDL_AUTO_DB2);
        //properties.put("hibernate.dialect","org.hibernate.dialect.MySQLDialect");

        return
                builder
                        .dataSource(dataSource)
                        .packages("de.bachelorarbeit.MicroserviceA.DB2.entities")
                        .persistenceUnit("DB2")
                        .properties(properties)
                        .build();
    }
    @Bean(name = "barTransactionManager")
    public PlatformTransactionManager barTransactionManager(
            @Qualifier("barEntityManagerFactory") EntityManagerFactory
                    barEntityManagerFactory
    ) {
        return new JpaTransactionManager(barEntityManagerFactory);
    }
}
