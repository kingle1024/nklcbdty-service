package com.nklcbdty.api.common;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableRedisRepositories
@Slf4j
public class RedisConfig {

    @Value("${spring.redis.password}")
    private String password;

    // 기본 설정. dev/prod 모두 단일 Redis 를 쓴다.
    // (dev = application-dev.properties 의 cloudtype 주소, prod = REDIS_HOST/REDIS_PORT 환경변수)
    @Bean
    @Profile("!cluster")
    public RedisConnectionFactory redisStandaloneConnectionFactory (
        @Value("${spring.redis.host}") String host,
        @Value("${spring.redis.port}") int port) {

        log.info("Redis Standalone Configuration: host={}, port={}", host, port);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setPassword(password);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)) // 연결 타임아웃 설정
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    // Redis Cluster 를 쓸 때만 'cluster' 프로파일로 켠다.
    // 예전 EC2 클러스터(spring.redis.cluster.nodes)는 지금 응답하지 않으므로 기본에서 제외했다.
    @Bean
    @Profile("cluster")
    public RedisConnectionFactory redisClusterConnectionFactory (
        @Value("${spring.redis.cluster.nodes}") String clusterNodes) {

        log.info("Redis Cluster Configuration: nodes={}", clusterNodes);
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
            Arrays.asList(clusterNodes.split(",")));
        clusterConfig.setPassword(password); // 비밀번호 설정

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)) // 연결 타임아웃 설정
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
}
