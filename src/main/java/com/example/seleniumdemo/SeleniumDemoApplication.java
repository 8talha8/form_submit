package com.example.seleniumdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching      // enables the mapping / session caches
@EnableAsync        // Selenium flows run on a dedicated thread pool
@EnableScheduling   // background cleanup of idle browser sessions
public class SeleniumDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeleniumDemoApplication.class, args);
    }
}
