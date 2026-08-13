package com.nklcbdty.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis 가 죽어 있어도 캐시를 붙인 메서드가 예외 없이 동작해야 한다.
 *
 * <p>목록 API 는 캐시 도입 전까지 Redis 와 무관하게 잘 동작했다. 스프링의 기본 동작은
 * 캐시 조회 실패 시 예외를 그대로 올려보내는 것이어서, 그대로 두면 새벽 재기동 구간에
 * {@code /api/list} 가 통째로 500 이 되는 퇴행이 생긴다.
 * {@link CacheConfig#errorHandler()} 가 그걸 막는지 고정한다.</p>
 */
class CacheFallbackTest {

    @Test
    void redis_가_죽어있으면_캐시를_건너뛰고_원래_메서드로_폴백한다() {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(TestConfig.class, CacheConfig.class)) {

            CountingService service = ctx.getBean(CountingService.class);

            // 예외가 나지 않고 값이 정상 반환되는 것이 핵심이다.
            assertThatNoException().isThrownBy(() -> service.value("A"));
            assertThat(service.value("A")).isEqualTo("v-A");

            // Redis 가 없으니 캐시 히트가 있을 수 없고, 매 호출이 실제 메서드로 내려간다.
            // (캐시가 살아 있으면 호출 수가 줄어든다 = 캐시가 먹고 있다는 뜻)
            // 필드가 아니라 메서드로 읽는다. service 는 CGLIB 프록시라 필드를 직접 보면 프록시 쪽 빈 값이 나온다.
            assertThat(service.callCount()).isGreaterThan(1);
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            // 아무도 듣고 있지 않은 포트. 접속이 즉시 거부돼 캐시 연산이 예외를 던진다.
            return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 63999),
                LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(200))
                    .build());
        }

        @Bean
        CountingService countingService() {
            return new CountingService();
        }
    }

    static class CountingService {

        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable(cacheNames = CacheConfig.JOB_LIST, key = "#key")
        public String value(String key) {
            calls.incrementAndGet();
            return "v-" + key;
        }

        public int callCount() {
            return calls.get();
        }
    }
}
