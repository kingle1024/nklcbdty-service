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
        validateRefreshToken(userId, refreshToken);
        utilityNklcb.validToken(refreshToken);

        // 새로운 Access Token 및 Refresh Token 생성
        String newAccessToken = utilityNklcb.generateToken(userId, false);
        String newRefreshToken = utilityNklcb.generateToken(userId, true);

        // Redis에 새로운 Refresh Token 저장
        tokenService.saveRefreshToken(userId, refreshToken);

        return createTokenResponse(newAccessToken, newRefreshToken);
    }
    private void validateRefreshToken(String userId, String refreshToken) {
        String storedRefreshToken = tokenService.getRefreshToken(userId);
        if (!refreshToken.equals(storedRefreshToken)) {
            throw new InvalidTokenException("Invalid Refresh Token");
        }
    }
    private TokenResponse createTokenResponse(String accessToken, String refreshToken) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }
}
