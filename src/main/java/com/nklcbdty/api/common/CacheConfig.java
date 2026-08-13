package com.nklcbdty.api.common;

import java.time.Duration;

import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.extern.slf4j.Slf4j;

/**
 * 목록 API 응답 캐시 설정.
 *
 * <p>{@code /api/list?company=ALL} 은 예열이 끝난 상태에서도 매 요청 1.5~3초가 걸린다.
 * 전건 조회 + annoId 중복제거 + 문자열 날짜 파싱 + 마감임박순 정렬 + 128KB 직렬화를
 * 요청마다 다시 하기 때문이다. 결과가 회사별로 하루 몇 번(크롤 시점)만 바뀌므로 캐싱한다.</p>
 *
 * <p>캐시에 넣는 값은 <b>완성된 응답 JSON 문자열</b>이다. 객체(List&lt;Job_mst&gt;)로 캐싱하면
 * Redis 에서 128KB 를 읽어 객체로 역직렬화한 뒤 응답으로 다시 직렬화하게 되어 이득이 크게 깎인다.
 * 직렬화가 끝난 문자열을 그대로 담아 조회를 Redis GET 한 번으로 끝낸다.</p>
 *
 * <p>Redis 영속성이 꺼져 있어(save 미설정 + appendonly=no) 새벽 재기동마다 캐시가 비지만,
 * 캐시는 날아가도 되는 데이터라 문제가 아니다. 아침 워밍업이 다시 채운다.
 * ({@code .github/workflows/cache-warmup.yml})</p>
 */
@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /** {@code /api/list} 응답. 키는 company 파라미터(ALL + CompanyEnums 8개 = 9개). */
    public static final String JOB_LIST = "jobList";

    /** {@code /api/category/list} 응답. 파라미터가 없어 키가 하나뿐이다. */
    public static final String CATEGORY_LIST = "categoryList";

    /**
     * {@code /api/calendar/deadlines} 응답. 키는 {@code company:yyyy-MM} 이다.
     *
     * <p>목록과 같은 전건 조회·중복제거·날짜 파싱을 매번 다시 하므로 같은 이유로 캐싱한다.
     * 달을 넘길 때마다 원본을 때리던 것이 캐시 히트로 끝난다. 응답에 "지금 기준 마감 여부" 같은
     * 시점 의존 값을 담지 않기 때문에(마감 판정은 프론트가 endDate 로 한다) 캐싱해도 안전하다.</p>
     */
    public static final String JOB_CALENDAR = "jobCalendar";

    /**
     * 무효화를 놓친 경우의 안전망. 크롤 후 @CacheEvict 가 정상 동작하면 여기까지 오지 않는다.
     * 아침에 한 번 채우고 하루 방치하면 그날 올라온 공고가 안 보이므로 짧게 잡는다.
     */
    private static final Duration TTL = Duration.ofHours(1);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 값이 전부 JSON 문자열이므로 키/값 모두 String 직렬화를 쓴다.
        // redis-cli 에서 "jobList::ALL" 처럼 그대로 읽을 수 있다.
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(TTL)
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }

    /**
     * Redis 가 죽어 있어도 목록 API 는 살아 있어야 한다.
     *
     * <p>스프링의 기본 동작은 캐시 조회 실패 시 예외를 그대로 올려보내는 것이다. 그러면
     * commandTimeout(2초)에 걸리는 새벽 재기동 구간에 목록 API 가 통째로 500 이 된다.
     * 캐시를 붙이기 전에는 Redis 상태와 무관하게 잘 동작했던 엔드포인트이므로 그건 순전히 퇴행이다.
     * 여기서 예외를 삼키면 스프링이 원래 메서드를 그대로 실행해 DB 조회로 폴백한다.</p>
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 조회 실패 — DB 조회로 폴백한다. cache={}, key={}, error={}",
                    cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패 — 응답은 정상 반환한다. cache={}, key={}, error={}",
                    cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // 무효화 실패는 stale 을 남기지만 TTL 이 걷어간다.
                log.warn("캐시 무효화 실패 — TTL({}분) 만료까지 stale 이 남는다. cache={}, key={}, error={}",
                    TTL.toMinutes(), cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("캐시 전체 삭제 실패 — TTL({}분) 만료까지 stale 이 남는다. cache={}, error={}",
                    TTL.toMinutes(), cache.getName(), exception.getMessage());
            }
        };
    }
}
