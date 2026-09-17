package com.nklcbdty.api.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklcbdty.api.common.UtilityNklcb;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 이메일로 로그인한 일반 사용자의 토큰(role=ADMIN)이 /api/admin/** 을 통과하는지 확인한다.
 * 관리자 계정(admin_account) 로그인 없이도 관리자 화면이 동작해야 하므로 이 계약이 깨지면 안 된다.
 */
class AdminUserTokenFilterTest {

    private static final String SECRET =
        Base64.getEncoder().encodeToString("nklcbdty-test-secret-key-for-hmac-sha-256!".getBytes());

    private UtilityNklcb utilityNklcb;
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() {
        utilityNklcb = new UtilityNklcb();
        ReflectionTestUtils.setField(utilityNklcb, "SECRET_KEY", SECRET);
        authFilter = new AuthFilter();
        ReflectionTestUtils.setField(authFilter, "SECRET_KEY", SECRET);
    }

    @Test
    void ADMIN_토큰이면_관리자_경로를_통과하고_표시이름을_넘겨준다() throws Exception {
        String token = utilityNklcb.generateAdminUserToken("local@3", "관리자");
        MockHttpServletRequest request = adminRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
        // userId("local@3")가 아니라 표시 이름이 넘어가야 관리자 글 작성자에 내부 식별자가 안 보인다.
        assertThat(request.getAttribute("adminUsername")).isEqualTo("관리자");
    }

    @Test
    void 일반_사용자_토큰으로는_관리자_경로에_못_들어간다() throws Exception {
        String token = utilityNklcb.generateToken("local@4", false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(adminRequest(token), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    private MockHttpServletRequest adminRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/subscriptions");
        request.setRequestURI("/api/admin/subscriptions");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
