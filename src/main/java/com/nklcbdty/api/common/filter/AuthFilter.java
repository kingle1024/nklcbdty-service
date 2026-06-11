package com.nklcbdty.api.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nklcbdty.api.common.security.AllowedPaths;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthFilter extends OncePerRequestFilter {
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();

        // 허용된 경로인지 확인
        for (String path : AllowedPaths.getAllowedPaths()) {
            if (requestUri.matches(path.replace("**", ".*"))) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 관리자 경로(/api/admin/**). 단 /api/admin/login 은 위 허용목록에서 이미 통과됨.
        // 나머지 관리자 경로는 ADMIN 역할이 담긴 토큰을 요구한다.
        if (requestUri.startsWith("/api/admin/")) {
            final String adminToken = getTokenByRequest(request);
            if (adminToken != null && validateToken(adminToken) && isAdminToken(adminToken)) {
                request.setAttribute("adminUsername", getUserIdByToken(adminToken));
                filterChain.doFilter(request, response);
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin authentication required");
            return;
        }

        final String token = getTokenByRequest(request);
        if (token != null && validateToken(token)) {
            String userId = getUserIdByToken(token);
            request.setAttribute("userId", userId);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    public String getUserIdByRequest(HttpServletRequest request) {
        final String token = getTokenByRequest(request);
        return getUserIdByToken(token);
    }

    // 토큰에 role=ADMIN 클레임이 있는지 검사
    private boolean isAdminToken(String token) {
        try {
            Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
            return "ADMIN".equals(claims.get("role", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private String getTokenByRequest(HttpServletRequest request) {
        final String bearerToken = request.getHeader("Authorization");
        if(bearerToken == null) {
            return null;
        }

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    public String getUserIdByToken(String token) {
        if(token == null) {
            return null;
        }

        try {
            Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.info("error {}", e.getMessage());
            return false;
        }
    }
}
