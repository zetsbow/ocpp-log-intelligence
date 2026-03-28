package com.ocpp.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.ocpp.web.mapper")
public class OcppWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(OcppWebApplication.class, args);
    }
}
