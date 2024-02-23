package de.bachelorarbeit.MicroserviceA.Configs;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean(name="keyEstablishmentResttemplate")
    public RestTemplate restTemplateKeyestablishment() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(3000))
                .setReadTimeout(Duration.ofMillis(3000))
                .build();
    }

    @Primary
    @Bean(name = "einzelanordnungResttemplate")
    public RestTemplate restTemplateEinzelanordnungen() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(350))
                .setReadTimeout(Duration.ofMillis(350))
                .build();
    }

}
