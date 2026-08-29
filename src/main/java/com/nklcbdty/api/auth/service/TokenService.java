package com.nklcbdty.api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.user.repository.RefreshTokenRepository;
import com.nklcbdty.api.user.vo.RefreshTokenVo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 리프레시 토큰 저장·검증.
 *
 * <p>DB(refresh_tokens)가 진실의 원천이고 Redis 는 빠른 조회용 캐시다.
 * 예전에는 Redis 에만 의존해서 Redis 가 비면(재시작·유실) 갱신이 전부 실패해
 * 사용자가 1시간마다 로그아웃되는 문제가 있었다.</p>
 *
 * <p>DB 검증은 userId+토큰해시 단위라 기기(브라우저)마다 각자의 리프레시 토큰이
 * 유효하다 — 다른 기기에서 로그인해도 기존 기기가 로그아웃되지 않는다.</p>
 *
 * <p>DB·Redis 어느 쪽에도 토큰 원문은 남기지 않는다. Redis 는 "이 토큰이 유효한가"만
 * 알면 되므로 해시를 키에 담고 존재 여부만 본다 — 덤프(RDB/AOF)가 유출돼도
 * 그 값으로 로그인할 수 없다.</p>
 */
@Slf4j
@Service
public class TokenService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long REFRESH_TOKEN_EXPIRATION_DAYS = UtilityNklcb.REFRESH_TOKEN_EXPIRATION_DAYS;
    /** 여러 탭이 같은 토큰으로 동시에 갱신을 부르면 한쪽은 회전 직후의 옛 토큰을 내민다. 그 유예 시간. */
    private static final long ROTATED_TOKEN_GRACE_SECONDS = 60;

    @Autowired
    public TokenService(RedisTemplate<String, Object> redisTemplate, RefreshTokenRepository refreshTokenRepository) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public void saveRefreshToken(String userId, String refreshToken) {
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

        try {
            RefreshTokenVo refreshTokenEntity = RefreshTokenVo.builder()
                .userId(userId)
                .token(hashToken(refreshToken)) // 원문 대신 해시를 저장
                .issuedAt(now)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
            refreshTokenRepository.save(refreshTokenEntity);
        } catch (Exception e) {
            // DB 저장이 실패하면 Redis 가 비었을 때 갱신이 실패한다. 반드시 로그로 남긴다.
            log.error("refresh token DB save failed for userId {}: {}", userId, e.getMessage());
        }

        try {
            redisTemplate.opsForValue()
                .set(cacheKey(userId, refreshToken), "1", Duration.ofDays(REFRESH_TOKEN_EXPIRATION_DAYS));
            log.info("refresh token saved for userId: {}", userId);
        } catch (Exception e) {
            log.warn("refresh token Redis save failed (DB fallback will be used) for userId {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 리프레시 토큰이 이 사용자에게 발급된 유효한 토큰인지 확인한다.
     * Redis 에 있으면 바로 통과, 없으면 DB 에서 해시로 찾는다.
     */
    public boolean isRefreshTokenValid(String userId, String refreshToken) {
        if (userId == null || refreshToken == null) {
            return false;
        }
        if (isCached(userId, refreshToken)) {
            return true;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            return refreshTokenRepository
                .findFirstByUserIdAndTokenOrderByIdDesc(userId, hashToken(refreshToken))
                .filter(t -> t.getExpiresAt().isAfter(now))
                .filter(t -> !t.isRevoked()
                    || (t.getRevokedAt() != null
                        && t.getRevokedAt().isAfter(now.minusSeconds(ROTATED_TOKEN_GRACE_SECONDS))))
                .isPresent();
        } catch (Exception e) {
            log.error("refresh token DB lookup failed for userId {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /** 갱신 성공 시 새 토큰을 저장하고 방금 쓴 옛 토큰은 무효화한다(유예 시간 동안은 통과). */
    public void rotateRefreshToken(String userId, String oldRefreshToken, String newRefreshToken) {
        saveRefreshToken(userId, newRefreshToken);
        // 캐시는 토큰별 키라 덮어써지지 않는다. 지우지 않으면 무효화한 토큰이 빠른 경로로 계속 통과한다.
        try {
            redisTemplate.delete(cacheKey(userId, oldRefreshToken));
        } catch (Exception e) {
            log.warn("old refresh token cache delete failed for userId {}: {}", userId, e.getMessage());
        }
        try {
            refreshTokenRepository
                .findFirstByUserIdAndTokenAndIsRevokedFalseOrderByIdDesc(userId, hashToken(oldRefreshToken))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    t.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(t);
                });
        } catch (Exception e) {
            log.warn("old refresh token revoke failed for userId {}: {}", userId, e.getMessage());
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 값이 아니라 키로 판별한다 — 캐시에 토큰 원문이 남지 않는다. */
    private String cacheKey(String userId, String refreshToken) {
        return "refresh:" + userId + ":" + hashToken(refreshToken);
    }

    private boolean isCached(String userId, String refreshToken) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey(userId, refreshToken)));
        } catch (Exception e) {
            return false;
        }
    }
}
