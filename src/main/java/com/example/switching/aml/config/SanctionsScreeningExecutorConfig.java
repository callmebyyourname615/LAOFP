package com.example.switching.aml.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("!migration")
public class SanctionsScreeningExecutorConfig {

    @Bean(name = "sanctionsScreeningExecutor")
    public Executor sanctionsScreeningExecutor(AmlProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getScreeningExecutorCoreSize());
        executor.setMaxPoolSize(properties.getScreeningExecutorMaxSize());
        executor.setQueueCapacity(properties.getScreeningExecutorQueueCapacity());
        executor.setThreadNamePrefix("sanctions-screen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
