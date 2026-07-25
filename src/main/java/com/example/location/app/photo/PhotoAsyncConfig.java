package com.example.location.app.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(PhotoFeatureProperties.class)
public class PhotoAsyncConfig {

    @Bean(name = "photoEnhancementExecutor")
    TaskExecutor photoEnhancementExecutor(PhotoFeatureProperties properties) {
        PhotoFeatureProperties.Processing processing = properties.getProcessing();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(processing.getCorePoolSize());
        executor.setMaxPoolSize(processing.getMaxPoolSize());
        executor.setQueueCapacity(processing.getQueueCapacity());
        executor.setThreadNamePrefix("photo-enhance-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
