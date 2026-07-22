package com.example.seleniumdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool so blocking Selenium calls never starve Tomcat's
 * request threads. Each browser flow runs on this pool.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public static final String FLOW_EXECUTOR = "flowExecutor";

    @Bean(name = FLOW_EXECUTOR)
    public Executor flowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("selenium-flow-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return flowExecutor();
    }
}
