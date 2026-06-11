package com.nklcbdty.api.admin.auth.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.admin.auth.repository.AdminAccountRepository;
import com.nklcbdty.api.admin.auth.vo.AdminAccount;
import com.nklcbdty.api.common.UtilityNklcb;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final UtilityNklcb utilityNklcb;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminAccountRepository adminAccountRepository, UtilityNklcb utilityNklcb) {
        this.adminAccountRepository = adminAccountRepository;
        this.utilityNklcb = utilityNklcb;
    }

    /**
     * username/password 검증 후 관리자 JWT(role=ADMIN) 발급.
     * 실패 시 IllegalArgumentException.
     */
    public LoginResult login(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            throw new IllegalArgumentException("아이디와 비밀번호를 입력하세요.");
        }
        Optional<AdminAccount> found = adminAccountRepository.findByUsername(username);
        if (found.isEmpty() || !passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        AdminAccount account = found.get();
        String token = utilityNklcb.generateAdminToken(account.getUsername());
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : account.getUsername();
        return new LoginResult(token, account.getUsername(), displayName);
    }

    /** 로그인 결과 (토큰 + 표시 정보) */
    public static class LoginResult {
        private final String token;
        private final String username;
        private final String displayName;

        public LoginResult(String token, String username, String displayName) {
            this.token = token;
            this.username = username;
            this.displayName = displayName;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
