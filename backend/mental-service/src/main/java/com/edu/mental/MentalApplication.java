package com.edu.mental;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.edu.mental.mapper")
public class MentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentalApplication.class, args);
    }
}
