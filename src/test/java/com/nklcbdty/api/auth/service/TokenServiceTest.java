package com.nklcbdty.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.nklcbdty.api.user.repository.RefreshTokenRepository;
import com.nklcbdty.api.user.vo.RefreshTokenVo;

/**
 * 로그인 유지의 핵심인 리프레시 토큰 검증을 고정한다.
 * 특히 "Redis 가 비어 있어도 DB 로 검증된다"가 깨지면
 * 사용자가 1시간마다 로그아웃되는 장애가 재발한다.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    private RefreshTokenVo dbRow(boolean revoked, LocalDateTime revokedAt, LocalDateTime expiresAt) {
        return RefreshTokenVo.builder()
            .id(1L)
            .userId("kakao@1")
            .token("stored-hash")
            .issuedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(expiresAt)
            .isRevoked(revoked)
            .revokedAt(revokedAt)
            .build();
    }

    @Test
    void 레디스가_비어_있어도_DB에_유효한_토큰이_있으면_통과한다() {
        // Redis 는 스텁하지 않는다(= 비어 있음/장애 상황과 동일)
        when(refreshTokenRepository.findFirstByUserIdAndTokenOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.of(dbRow(false, null, LocalDateTime.now().plusDays(10))));

        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isTrue();
    }

    @Test
    void 레디스에_같은_토큰이_있으면_DB를_보지_않고_통과한다() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isTrue();
    }

    @Test
    void 캐시에는_토큰_원문이_아니라_해시가_담긴다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenService.saveRefreshToken("kakao@1", "refresh-token");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), any(), any(Duration.class));
        assertThat(key.getValue()).startsWith("refresh:kakao@1:").doesNotContain("refresh-token");
        assertThat(key.getValue().substring("refresh:kakao@1:".length())).hasSize(64);
    }

    @Test
    void 회전하면_옛_토큰의_캐시_키를_지운다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenService.rotateRefreshToken("kakao@1", "old-refresh", "new-refresh");

        // 지우지 않으면 무효화한 토큰이 TTL 동안 빠른 경로로 계속 통과한다
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(deleted.capture());
        assertThat(deleted.getValue()).startsWith("refresh:kakao@1:").doesNotContain("old-refresh");
    }

    @Test
    void DB에도_없는_토큰은_거부한다() {
        when(refreshTokenRepository.findFirstByUserIdAndTokenOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.empty());

        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isFalse();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        when(refreshTokenRepository.findFirstByUserIdAndTokenOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.of(dbRow(false, null, LocalDateTime.now().minusMinutes(1))));

        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isFalse();
    }

    @Test
    void 회전_직후의_옛_토큰은_유예시간_안에서만_통과한다() {
        // 동시 401 을 받은 다른 탭이 옛 토큰으로 갱신을 다시 부르는 상황
        when(refreshTokenRepository.findFirstByUserIdAndTokenOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.of(dbRow(true, LocalDateTime.now().minusSeconds(5), LocalDateTime.now().plusDays(10))));
        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isTrue();

        when(refreshTokenRepository.findFirstByUserIdAndTokenOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.of(dbRow(true, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusDays(10))));
        assertThat(tokenService.isRefreshTokenValid("kakao@1", "refresh-token")).isFalse();
    }

    @Test
    void 저장할_때_원문이_아니라_해시를_DB에_남기고_만료는_30일이다() {
        tokenService.saveRefreshToken("kakao@1", "refresh-token");

        ArgumentCaptor<RefreshTokenVo> captor = ArgumentCaptor.forClass(RefreshTokenVo.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenVo saved = captor.getValue();
        assertThat(saved.getToken()).isNotEqualTo("refresh-token").hasSize(64); // SHA-256 hex
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void 회전하면_새_토큰을_저장하고_옛_토큰_행을_무효화한다() {
        RefreshTokenVo oldRow = dbRow(false, null, LocalDateTime.now().plusDays(10));
        when(refreshTokenRepository.findFirstByUserIdAndTokenAndIsRevokedFalseOrderByIdDesc(eq("kakao@1"), anyString()))
            .thenReturn(Optional.of(oldRow));

        tokenService.rotateRefreshToken("kakao@1", "old-refresh", "new-refresh");

        ArgumentCaptor<RefreshTokenVo> captor = ArgumentCaptor.forClass(RefreshTokenVo.class);
        verify(refreshTokenRepository, atLeastOnce()).save(captor.capture());
        List<RefreshTokenVo> savedAll = captor.getAllValues();

        // 새 토큰 행이 저장됐다
        assertThat(savedAll).anySatisfy(v -> {
            assertThat(v.isRevoked()).isFalse();
            assertThat(v.getToken()).hasSize(64);
        });
        // 옛 행은 무효화됐다
        assertThat(oldRow.isRevoked()).isTrue();
        assertThat(oldRow.getRevokedAt()).isNotNull();
    }

    @Test
    void 레디스가_죽어도_저장은_예외를_내지_않는다() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        tokenService.saveRefreshToken("kakao@1", "refresh-token");

        verify(refreshTokenRepository).save(any(RefreshTokenVo.class));
    }
}
