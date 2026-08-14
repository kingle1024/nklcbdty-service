package com.nklcbdty.api.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.auth.dto.TokenResponse;
import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.exception.InvalidTokenException;

@Service
public class AuthService {
    private final TokenService tokenService;
    private final UtilityNklcb utilityNklcb;

    @Autowired
    public AuthService(TokenService tokenService, UtilityNklcb utilityNklcb) {
        this.tokenService = tokenService;
        this.utilityNklcb = utilityNklcb;
    }

    public TokenResponse refreshAccessToken(String userId, String refreshToken) {
        utilityNklcb.validToken(refreshToken);
        if (!tokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new InvalidTokenException("Invalid Refresh Token");
        }

        // 새로운 Access Token 및 Refresh Token 생성
        String newAccessToken = utilityNklcb.generateToken(userId, false);
        String newRefreshToken = utilityNklcb.generateToken(userId, true);

        // 새 Refresh Token 을 저장하고 방금 쓴 토큰은 무효화한다.
        // (예전에는 여기서 새 토큰 대신 옛 토큰을 다시 저장해서, 클라이언트가 받은
        //  새 토큰이 저장소와 어긋나 두 번째 갱신부터 전부 실패 → 강제 로그아웃됐다)
        tokenService.rotateRefreshToken(userId, refreshToken, newRefreshToken);

        return createTokenResponse(newAccessToken, newRefreshToken);
    }
    private TokenResponse createTokenResponse(String accessToken, String refreshToken) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }
}
