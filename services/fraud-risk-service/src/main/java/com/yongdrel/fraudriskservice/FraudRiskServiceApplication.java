package com.yongdrel.fraudriskservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudRiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudRiskServiceApplication.class, args);
    }
}
