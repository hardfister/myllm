package com.example.myllm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching  // 开启 Spring Cache（Redis 后端）
public class MyllmApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyllmApplication.class, args);
    }

}
