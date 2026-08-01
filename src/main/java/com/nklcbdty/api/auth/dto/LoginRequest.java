package com.nklcbdty.api.auth.dto;

import lombok.Data;

/** 자체 로그인(이메일+비밀번호) 요청 */
@Data
public class LoginRequest {

    private String email;

    private String password;
}
