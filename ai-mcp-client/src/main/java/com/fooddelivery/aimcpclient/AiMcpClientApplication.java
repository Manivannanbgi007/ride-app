package com.fooddelivery.aimcpclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiMcpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiMcpClientApplication.class, args);
    }
}
