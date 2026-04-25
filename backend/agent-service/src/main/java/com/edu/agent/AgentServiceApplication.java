package com.edu.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(    exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.edu.agent.feign")
@MapperScan("com.edu.agent.mapper")
public class AgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}