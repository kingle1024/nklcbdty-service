package com.nklcbdty.api.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TokenService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public TokenService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveRefreshToken(String userId, String refreshToken) {
        try {
            redisTemplate.opsForValue().set(userId + ":refreshToken", refreshToken);
        } catch (RedisConnectionException e) {
            log.info("error {}", e.getMessage());
        } catch (Exception e) {
            log.info("error {}", e.getMessage());
        }
    }

    public String getRefreshToken(String userId) {
        try {
            Object token = redisTemplate.opsForValue().get(userId + ":refreshToken");

            if (token == null) {
                return null;
            }

            return (String)redisTemplate.opsForValue().get(userId + ":refreshToken");
        } catch (Exception e) {
            return null;
        }
    }
}
