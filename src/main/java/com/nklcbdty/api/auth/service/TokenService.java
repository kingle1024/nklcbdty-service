package com.nklcbdty.api.auth.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.nklcbdty.api.user.repository.RefreshTokenRepository;
import com.nklcbdty.api.user.vo.RefreshTokenVo;

import io.lettuce.core.RedisConnectionException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TokenService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    @Autowired
    public TokenService(RedisTemplate<String, Object> redisTemplate, RefreshTokenRepository refreshTokenRepository) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public void saveRefreshToken(String userId, String refreshToken) {
        try {
            redisTemplate.opsForValue().set(userId + ":refreshToken", refreshToken);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusDays(REFRESH_TOKEN_EXPIRATION_DAYS);
            String ipAddress = null;
            String userAgent = null;

            try {
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            } catch (IllegalStateException e) {
                log.warn("Cannot get HttpServletRequest outside of web request context: {}", e.getMessage());
            }
            
            String hashedRefreshToken = hashToken(refreshToken); // 별도의 해싱 유틸리티 필요
            
            RefreshTokenVo refreshTokenEntity = RefreshTokenVo.builder()
                .userId(userId)
                .token(hashedRefreshToken) // 해싱된 토큰 저장
                .issuedAt(now) // 발급 시각
                .expiresAt(expiresAt) // 만료 시각
                .isRevoked(false) // 처음 발급될 때는 무효화되지 않음
                .ipAddress(ipAddress) // IP 주소
                .userAgent(userAgent) // User-Agent
                .build();
            refreshTokenRepository.save(refreshTokenEntity);

            log.info("Redis token created: {}, {}. token saved for userId: {}", userId + ":refreshToken", refreshToken, userId);
        } catch (RedisConnectionException e) {
            log.info("error {}", e.getMessage());
        } catch (Exception e) {
            log.info("error {}", e.getMessage());
        }
    }

    private String hashToken(String token) {
        return "hashed_" + token;
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
