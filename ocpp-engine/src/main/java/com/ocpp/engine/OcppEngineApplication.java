package com.ocpp.engine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ocpp.engine.mapper")
public class OcppEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(OcppEngineApplication.class, args);
    }
}
