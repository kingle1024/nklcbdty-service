package com.nklcbdty.api.crawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration // 설정 클래스임을 명시
@EnableAsync // 비동기 기능 활성화
public class AsyncConfig {
    // 비동기 작업을 위한 스레드 풀 설정 (선택 사항이지만 권장)
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 스레드 수
        executor.setMaxPoolSize(20); // 최대 스레드 수
        executor.setQueueCapacity(100); // 작업 큐 용량
        executor.setThreadNamePrefix("Async-"); // 스레드 이름 접두사
        executor.initialize();
        return executor;
    }
}
