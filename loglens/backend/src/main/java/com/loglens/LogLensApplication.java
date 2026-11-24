package com.loglens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogLensApplication.class, args);
    }
}
