package icu.neurospicy.fibi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

import static icu.neurospicy.fibi.CucumberTestContextConfiguration.radicaleCalendar;

@ComponentScan
public class TestConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
