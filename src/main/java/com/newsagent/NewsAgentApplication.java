package com.newsagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NewsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsAgentApplication.class, args);
    }
}
