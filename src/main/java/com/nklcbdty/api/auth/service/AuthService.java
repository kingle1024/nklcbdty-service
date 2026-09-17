package com.nklcbdty.api.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.auth.dto.TokenResponse;
import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.common.security.AdminEmailPolicy;
import com.nklcbdty.api.exception.InvalidTokenException;

@Service
public class AuthService {
    private final TokenService tokenService;
    private final UtilityNklcb utilityNklcb;
    private final AdminEmailPolicy adminEmailPolicy;

    @Autowired
    public AuthService(TokenService tokenService, UtilityNklcb utilityNklcb, AdminEmailPolicy adminEmailPolicy) {
        this.tokenService = tokenService;
        this.utilityNklcb = utilityNklcb;
        this.adminEmailPolicy = adminEmailPolicy;
    }

    public TokenResponse refreshAccessToken(String userId, String refreshToken) {
        utilityNklcb.validToken(refreshToken);
        if (!tokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new InvalidTokenException("Invalid Refresh Token");
        }

        // 새로운 Access Token 및 Refresh Token 생성.
        // 관리자 이메일로 로그인한 사용자는 갱신된 토큰에도 ADMIN 을 유지해야 한다.
        // (안 그러면 1시간마다 관리자 화면에서 튕긴다)
        String adminName = adminEmailPolicy.adminNameByUserId(userId);
        String newAccessToken = adminName != null
            ? utilityNklcb.generateAdminUserToken(userId, adminName)
            : utilityNklcb.generateToken(userId, false);
        String newRefreshToken = utilityNklcb.generateToken(userId, true);

        // 새 Refresh Token 을 저장하고 방금 쓴 토큰은 무효화한다.
        // (예전에는 여기서 새 토큰 대신 옛 토큰을 다시 저장해서, 클라이언트가 받은
        //  새 토큰이 저장소와 어긋나 두 번째 갱신부터 전부 실패 → 강제 로그아웃됐다)
        tokenService.rotateRefreshToken(userId, refreshToken, newRefreshToken);

        return createTokenResponse(newAccessToken, newRefreshToken, adminName != null);
    }
    private TokenResponse createTokenResponse(String accessToken, String refreshToken, boolean admin) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setAdmin(admin);
        return response;
    }
}
