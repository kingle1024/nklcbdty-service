package com.nklcbdty.api.auth.dto;

import com.nklcbdty.api.user.vo.UserVo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {
    private String refreshToken;
    private UserVo userVo;
}
