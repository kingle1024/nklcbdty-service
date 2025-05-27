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

@Configuration
@EnableRedisRepositories
public class RedisConfig {

    @Value("${spring.redis.password}")
    private String password;

    // 개발 환경 설정
    @Bean
    @Profile("dev")
    // @Profile("prod")
    public RedisConnectionFactory redisStandaloneConnectionFactory (
        @Value("${spring.redis.host}") String host,
        @Value("${spring.redis.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setPassword(password);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)) // 연결 타임아웃 설정
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    // 운영 환경 설정
    @Bean
    @Profile("prod")
    // @Profile("dev")
    public RedisConnectionFactory redisClusterConnectionFactory (
        @Value("${spring.redis.cluster.nodes}") String clusterNodes) {

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
            Arrays.asList(clusterNodes.split(",")));
        // clusterConfig.setPassword(password); // 비밀번호 설정

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
