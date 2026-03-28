package com.ocpp.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OcppWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(OcppWebApplication.class, args);
    }
}
