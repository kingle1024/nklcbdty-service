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
import com.nklcbdty.api.common.security.AdminEmailPolicy;
import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

    @Mock
    private LocalAccountRepository localAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private UtilityNklcb utilityNklcb;

    @Mock
    private AdminEmailPolicy adminEmailPolicy;

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

    // --------------------------------------------------------------- 관리자

    @Test
    void 관리자_이메일로_로그인하면_ADMIN_토큰을_주고_isAdmin_을_알려준다() {
        LocalAccount stored = account(3L, "teran1024@naver.com", "password123");
        when(localAccountRepository.findByEmail("teran1024@naver.com")).thenReturn(Optional.of(stored));
        when(userRepository.findByUserId("local@3")).thenReturn(
            UserVo.builder().userId("local@3").username("관리자").email("teran1024@naver.com").build());
        when(adminEmailPolicy.isAdminEmail("teran1024@naver.com")).thenReturn(true);
        when(utilityNklcb.generateAdminUserToken("local@3", "관리자")).thenReturn("admin-access");
        when(utilityNklcb.generateToken("local@3", true)).thenReturn("refresh-token");

        LocalAuthService.AuthResult result = service.login("teran1024@naver.com", "password123");

        assertThat(result.admin()).isTrue();
        assertThat(result.token()).isEqualTo("admin-access");
        verify(utilityNklcb, never()).generateToken("local@3", false);
    }

    @Test
    void 관리자_이메일이_아니면_평소대로_일반_토큰을_준다() {
        LocalAccount stored = account(4L, "user@example.com", "password123");
        when(localAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));
        when(userRepository.findByUserId("local@4")).thenReturn(
            UserVo.builder().userId("local@4").username("유저").email("user@example.com").build());
        when(utilityNklcb.generateToken("local@4", false)).thenReturn("access-token");
        when(utilityNklcb.generateToken("local@4", true)).thenReturn("refresh-token");

        LocalAuthService.AuthResult result = service.login("user@example.com", "password123");

        assertThat(result.admin()).isFalse();
        assertThat(result.token()).isEqualTo("access-token");
        verify(utilityNklcb, never()).generateAdminUserToken(anyString(), anyString());
    }

    @Test
    void 관리자_이메일로_로그인하면_비어있던_프로필_이메일을_채운다() {
        // 토큰 갱신은 user 테이블 이메일로 관리자 여부를 다시 판정한다. 비어 있으면 1시간 뒤 권한이 풀린다.
        LocalAccount stored = account(3L, "teran1024@naver.com", "password123");
        UserVo profile = UserVo.builder().userId("local@3").username("관리자").build();
        when(localAccountRepository.findByEmail("teran1024@naver.com")).thenReturn(Optional.of(stored));
        when(userRepository.findByUserId("local@3")).thenReturn(profile);
        when(adminEmailPolicy.isAdminEmail("teran1024@naver.com")).thenReturn(true);
        when(utilityNklcb.generateAdminUserToken("local@3", "관리자")).thenReturn("admin-access");
        when(utilityNklcb.generateToken("local@3", true)).thenReturn("refresh-token");

        service.login("teran1024@naver.com", "password123");

        ArgumentCaptor<UserVo> saved = ArgumentCaptor.forClass(UserVo.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("teran1024@naver.com");
    }

    @Test
    void 관리자_이메일로는_회원가입할_수_없다() {
        // 이메일 인증이 없어서 선점 가입으로 관리자 권한을 가져갈 수 있기 때문이다.
        when(adminEmailPolicy.isAdminEmail("teran1024@naver.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup("teran1024@naver.com", "password123", "누구"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("회원가입할 수 없습니다");

        verify(localAccountRepository, never()).saveAndFlush(any(LocalAccount.class));
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
