package com.nklcbdty.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.auth.dto.TokenResponse;
import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.exception.InvalidTokenException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UtilityNklcb utilityNklcb;

    @InjectMocks
    private AuthService authService;

    @Test
    void 갱신에_성공하면_클라이언트에_준_새_리프레시_토큰을_그대로_저장한다() {
        // 예전 버그: 새 토큰을 응답으로 주고 저장은 옛 토큰을 해서
        // 두 번째 갱신부터 전부 실패 → 1~2시간마다 강제 로그아웃됐다.
        when(tokenService.isRefreshTokenValid("kakao@1", "old-refresh")).thenReturn(true);
        when(utilityNklcb.generateToken("kakao@1", false)).thenReturn("new-access");
        when(utilityNklcb.generateToken("kakao@1", true)).thenReturn("new-refresh");

        TokenResponse response = authService.refreshAccessToken("kakao@1", "old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        // 저장(회전)되는 토큰 = 응답으로 나간 토큰
        verify(tokenService).rotateRefreshToken("kakao@1", "old-refresh", "new-refresh");
    }

    @Test
    void 저장소에_없는_리프레시_토큰이면_거부한다() {
        when(tokenService.isRefreshTokenValid("kakao@1", "stolen-refresh")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshAccessToken("kakao@1", "stolen-refresh"))
            .isInstanceOf(InvalidTokenException.class);
    }
}
