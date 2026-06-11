package com.nklcbdty.api.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nklcbdty.api.admin.auth.repository.AdminAccountRepository;
import com.nklcbdty.api.admin.auth.vo.AdminAccount;
import com.nklcbdty.api.common.UtilityNklcb;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;

    @Mock
    private UtilityNklcb utilityNklcb;

    private AdminAccount account(String username, String rawPassword) {
        AdminAccount a = new AdminAccount();
        a.setUsername(username);
        a.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        a.setDisplayName("관리자");
        return a;
    }

    @Test
    void login_올바른자격증명이면_토큰을_발급한다() {
        when(adminAccountRepository.findByUsername("admin"))
            .thenReturn(Optional.of(account("admin", "admin1234")));
        when(utilityNklcb.generateAdminToken("admin")).thenReturn("admin.jwt.token");

        AdminAuthService service = new AdminAuthService(adminAccountRepository, utilityNklcb);
        AdminAuthService.LoginResult result = service.login("admin", "admin1234");

        assertThat(result.getToken()).isEqualTo("admin.jwt.token");
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    void login_비밀번호가틀리면_예외() {
        when(adminAccountRepository.findByUsername("admin"))
            .thenReturn(Optional.of(account("admin", "admin1234")));

        AdminAuthService service = new AdminAuthService(adminAccountRepository, utilityNklcb);

        assertThatThrownBy(() -> service.login("admin", "wrongpass"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_없는계정이면_예외() {
        when(adminAccountRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        AdminAuthService service = new AdminAuthService(adminAccountRepository, utilityNklcb);

        assertThatThrownBy(() -> service.login("nobody", "whatever"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
