package com.nklcbdty.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nklcbdty.api.auth.repository.LocalAccountRepository;
import com.nklcbdty.api.auth.vo.LocalAccount;
import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.user.repository.UserProfileRepository;
import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

    @Mock
    private LocalAccountRepository localAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private UtilityNklcb utilityNklcb;

    @InjectMocks
    private LocalAuthService service;

    // --------------------------------------------------------------- 회원가입

    @Test
    void signup_성공하면_local계정과_user프로필을_만들고_토큰을_발급한다() {
        when(localAccountRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(localAccountRepository.saveAndFlush(any(LocalAccount.class))).thenAnswer(inv -> {
            LocalAccount account = inv.getArgument(0);
            account.setId(7L);
            return account;
        });
        when(localAccountRepository.save(any(LocalAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilityNklcb.generateToken("local@7", false)).thenReturn("access-token");
        when(utilityNklcb.generateToken("local@7", true)).thenReturn("refresh-token");

        LocalAuthService.AuthResult result = service.signup("Test@Example.com", "password123", "테스터");

        assertThat(result.userId()).isEqualTo("local@7");
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.nickname()).isEqualTo("테스터");

        // 비밀번호는 BCrypt 해시로 저장된다
        ArgumentCaptor<LocalAccount> accountCaptor = ArgumentCaptor.forClass(LocalAccount.class);
        verify(localAccountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("test@example.com");
        assertThat(new BCryptPasswordEncoder().matches("password123", accountCaptor.getValue().getPasswordHash()))
            .isTrue();

        // user 테이블 프로필 생성
        ArgumentCaptor<UserVo> userCaptor = ArgumentCaptor.forClass(UserVo.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserId()).isEqualTo("local@7");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("테스터");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("test@example.com");

        verify(tokenService, times(1)).saveRefreshToken("local@7", "refresh-token");
    }

    @Test
    void signup_닉네임을_생략하면_이메일_앞부분을_쓴다() {
        when(localAccountRepository.existsByEmail("hong@example.com")).thenReturn(false);
        when(localAccountRepository.saveAndFlush(any(LocalAccount.class))).thenAnswer(inv -> {
            LocalAccount account = inv.getArgument(0);
            account.setId(1L);
            return account;
        });
        when(localAccountRepository.save(any(LocalAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilityNklcb.generateToken(anyString(), eq(false))).thenReturn("a");
        when(utilityNklcb.generateToken(anyString(), eq(true))).thenReturn("r");

        LocalAuthService.AuthResult result = service.signup("hong@example.com", "password123", null);

        assertThat(result.nickname()).isEqualTo("hong");
    }

    @Test
    void signup_같은이메일의_카카오계정이_있으면_새_userId_대신_그_계정에_연동한다() {
        when(localAccountRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userProfileRepository.findByEmailOrderByIdAsc("test@example.com")).thenReturn(List.of(
            UserVo.builder().id(1L).userId("kakao@2614615415").username("엄지").email("test@example.com").build()
        ));
        when(localAccountRepository.saveAndFlush(any(LocalAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUserId("kakao@2614615415"))
            .thenReturn(UserVo.builder().userId("kakao@2614615415").username("엄지").build());
        when(utilityNklcb.generateToken("kakao@2614615415", false)).thenReturn("access-token");
        when(utilityNklcb.generateToken("kakao@2614615415", true)).thenReturn("refresh-token");

        LocalAuthService.AuthResult result = service.signup("Test@Example.com", "password123", "새닉네임");

        // 기존 계정의 userId 를 그대로 쓴다 = 구독 설정/캘린더가 그대로 보인다
        assertThat(result.userId()).isEqualTo("kakao@2614615415");
        assertThat(result.nickname()).isEqualTo("엄지");
        verify(tokenService, times(1)).saveRefreshToken("kakao@2614615415", "refresh-token");

        // 자격증명만 추가하고 프로필(user 행)은 새로 만들지 않는다
        ArgumentCaptor<LocalAccount> accountCaptor = ArgumentCaptor.forClass(LocalAccount.class);
        verify(localAccountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUserId()).isEqualTo("kakao@2614615415");
        assertThat(new BCryptPasswordEncoder().matches("password123", accountCaptor.getValue().getPasswordHash()))
            .isTrue();
        verify(userRepository, never()).save(any(UserVo.class));
        verify(localAccountRepository, never()).save(any(LocalAccount.class));
    }

    @Test
    void signup_같은이메일_계정이_local이면_연동하지않고_새로_만든다() {
        when(localAccountRepository.existsByEmail("test@example.com")).thenReturn(false);
        // 자격증명이 정리되지 않고 남은 local@ 프로필은 연동 대상이 아니다
        when(userProfileRepository.findByEmailOrderByIdAsc("test@example.com")).thenReturn(List.of(
            UserVo.builder().id(4L).userId("local@3").username("엄지용").email("test@example.com").build()
        ));
        when(localAccountRepository.saveAndFlush(any(LocalAccount.class))).thenAnswer(inv -> {
            LocalAccount account = inv.getArgument(0);
            account.setId(9L);
            return account;
        });
        when(localAccountRepository.save(any(LocalAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilityNklcb.generateToken("local@9", false)).thenReturn("a");
        when(utilityNklcb.generateToken("local@9", true)).thenReturn("r");

        LocalAuthService.AuthResult result = service.signup("test@example.com", "password123", "테스터");

        assertThat(result.userId()).isEqualTo("local@9");
        verify(userRepository).save(any(UserVo.class));
    }

    @Test
    void signup_이미가입된_이메일이면_거절한다() {
        when(localAccountRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup("dup@example.com", "password123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미 가입된");

        verify(localAccountRepository, never()).saveAndFlush(any(LocalAccount.class));
    }

    @Test
    void signup_이메일형식이_틀리면_거절한다() {
        assertThatThrownBy(() -> service.signup("not-an-email", "password123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이메일 형식");
    }

    @Test
    void signup_비밀번호가_짧으면_거절한다() {
        assertThatThrownBy(() -> service.signup("test@example.com", "short", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비밀번호는");
    }

    // ----------------------------------------------------------------- 로그인

    @Test
    void login_이메일과_비밀번호가_맞으면_토큰을_발급한다() {
        LocalAccount account = account(7L, "test@example.com", "password123");
        when(localAccountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(userRepository.findByUserId("local@7"))
            .thenReturn(UserVo.builder().userId("local@7").username("테스터").build());
        when(utilityNklcb.generateToken("local@7", false)).thenReturn("access-token");
        when(utilityNklcb.generateToken("local@7", true)).thenReturn("refresh-token");

        LocalAuthService.AuthResult result = service.login("Test@Example.com", "password123");

        assertThat(result.userId()).isEqualTo("local@7");
        assertThat(result.nickname()).isEqualTo("테스터");
        verify(tokenService, times(1)).saveRefreshToken("local@7", "refresh-token");
    }

    @Test
    void login_비밀번호가_틀리면_사유를_구분하지않고_거절한다() {
        LocalAccount account = account(7L, "test@example.com", "password123");
        when(localAccountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login("test@example.com", "wrong-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이메일 또는 비밀번호");

        verify(tokenService, never()).saveRefreshToken(anyString(), anyString());
    }

    @Test
    void login_가입되지않은_이메일이면_같은_메세지로_거절한다() {
        when(localAccountRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("none@example.com", "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이메일 또는 비밀번호");
    }

    @Test
    void login_입력이_비어있으면_거절한다() {
        assertThatThrownBy(() -> service.login(" ", "password123"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.login("test@example.com", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- 중복 확인

    @Test
    void emailExists_대소문자를_무시하고_확인한다() {
        when(localAccountRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThat(service.emailExists("Test@Example.com ")).isTrue();
        assertThat(service.emailExists(null)).isFalse();
        assertThat(service.emailExists("  ")).isFalse();
    }

    // ------------------------------------------------------------------ 헬퍼

    private LocalAccount account(Long id, String email, String rawPassword) {
        LocalAccount account = new LocalAccount();
        account.setId(id);
        account.setEmail(email);
        account.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        account.setUserId(LocalAccount.USER_ID_PREFIX + id);
        return account;
    }
}
