package com.nklcbdty.api.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 작업용 스레드 풀.
 *
 * <p>스프링 기본 스케줄러는 스레드가 하나라 등록된 작업이 서로를 막는다. 본문 수집
 * ({@code JobContentIndexer})은 외부 사이트를 순서대로 받느라 한 주기가 수십 초씩 걸리는데,
 * 그동안 임베딩 색인({@code JobEmbeddingIndexer}, 60초 주기)이 통째로 밀린다.
 * 풀을 주면 각자 자기 주기대로 돈다.</p>
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("Scheduled-");
        // 종료 시 돌던 작업이 끊기지 않게 한다(본문 수집 중 커넥션이 끊기면 실패로 기록된다).
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
