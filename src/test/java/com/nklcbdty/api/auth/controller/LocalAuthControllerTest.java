package com.nklcbdty.api.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.auth.service.LocalAuthService;

class LocalAuthControllerTest {

    private LocalAuthService localAuthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        localAuthService = mock(LocalAuthService.class);
        mockMvc = standaloneSetup(new LocalAuthController(localAuthService)).build();
    }

    @Test
    @DisplayName("POST /api/auth/signup: 카카오 로그인과 같은 키(token/refreshToken/userId/nickname)로 응답한다")
    void signup_returnsKakaoShapedBody() throws Exception {
        when(localAuthService.signup("test@example.com", "password123", "테스터"))
            .thenReturn(new LocalAuthService.AuthResult("access", "refresh", "local@7", "테스터"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\",\"nickname\":\"테스터\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access"))
            .andExpect(jsonPath("$.refreshToken").value("refresh"))
            .andExpect(jsonPath("$.userId").value("local@7"))
            .andExpect(jsonPath("$.nickname").value("테스터"));
    }

    @Test
    @DisplayName("POST /api/auth/signup: 검증 실패는 400 과 메세지를 돌려준다")
    void signup_invalidInputReturns400() throws Exception {
        when(localAuthService.signup(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("이미 가입된 이메일입니다."));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@example.com\",\"password\":\"password123\",\"nickname\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
    }

    @Test
    @DisplayName("POST /api/auth/login: 성공하면 토큰을 돌려준다")
    void login_returnsTokens() throws Exception {
        when(localAuthService.login("test@example.com", "password123"))
            .thenReturn(new LocalAuthService.AuthResult("access", "refresh", "local@7", "테스터"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access"))
            .andExpect(jsonPath("$.userId").value("local@7"));
    }

    @Test
    @DisplayName("POST /api/auth/login: 자격증명이 틀리면 401 이다")
    void login_badCredentialsReturns401() throws Exception {
        when(localAuthService.login(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("GET /api/auth/email-exists: 중복 여부를 exists 로 돌려준다")
    void emailExists_returnsFlag() throws Exception {
        when(localAuthService.emailExists("dup@example.com")).thenReturn(true);

        mockMvc.perform(get("/api/auth/email-exists").param("email", "dup@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true));
    }
}
