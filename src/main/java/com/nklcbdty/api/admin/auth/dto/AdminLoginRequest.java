package com.nklcbdty.api.admin.auth.dto;

import lombok.Data;

/** 관리자 로그인 요청 본문 */
@Data
public class AdminLoginRequest {
    private String username;
    private String password;
}
