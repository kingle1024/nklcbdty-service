package com.nklcbdty.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenResponse {
    private String accessToken;
    private String refreshToken;

    /**
     * 새 access 토큰이 관리자 권한을 가졌는지. 프론트는 이 값으로 관리자 메뉴 표시를 유지한다.
     * (필드명을 admin 으로 두면 Jackson 이 "admin" 으로 내보내므로 로그인 응답과 키를 맞춘다)
     */
    @JsonProperty("isAdmin")
    private boolean admin;
}
