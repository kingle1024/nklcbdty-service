package com.nklcbdty.api.auth.dto;

import lombok.Data;

/** 자체 회원가입 요청 */
@Data
public class SignupRequest {

    private String email;

    private String password;

    /** 표시용 닉네임. 생략하면 이메일 앞부분을 쓴다. */
    private String nickname;
}
