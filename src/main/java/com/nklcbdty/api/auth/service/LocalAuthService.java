package com.nklcbdty.api.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.auth.repository.LocalAccountRepository;
import com.nklcbdty.api.auth.vo.LocalAccount;
import com.nklcbdty.api.common.UtilityNklcb;
import com.nklcbdty.api.user.repository.UserProfileRepository;
import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 자체 회원가입/로그인(이메일+비밀번호).
 * 카카오 로그인(KakaoController)과 같은 토큰 체계를 쓴다:
 * userId 로 access/refresh JWT 를 발급하고 user 테이블에 프로필 행을 만든다.
 * 카카오 사용자는 "kakao@{id}", 자체 가입 사용자는 "local@{id}" userId 를 갖는다.
 *
 * <p>단, 이미 같은 이메일로 쓰던 계정(예: 카카오 로그인)이 있으면 새 userId 를 만들지 않고
 * 그 계정에 비밀번호 자격증명만 붙인다. 그래야 구독 설정·캘린더가 로그인 방식에 따라 갈리지 않는다.
 * (이메일 인증 절차가 없으므로, 남이 내 이메일로 가입해 계정을 이어받는 경로가 열려 있다는 점은 감수한 선택이다.)
 */
@Slf4j
@Service
public class LocalAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final LocalAccountRepository localAccountRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TokenService tokenService;
    private final UtilityNklcb utilityNklcb;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LocalAuthService(LocalAccountRepository localAccountRepository, UserRepository userRepository,
                            UserProfileRepository userProfileRepository, TokenService tokenService,
                            UtilityNklcb utilityNklcb) {
        this.localAccountRepository = localAccountRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.tokenService = tokenService;
        this.utilityNklcb = utilityNklcb;
    }

    /**
     * 회원가입. 성공하면 바로 로그인 상태가 되도록 토큰까지 발급해 돌려준다.
     */
    @Transactional
    public AuthResult signup(String email, String rawPassword, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(rawPassword);

        if (localAccountRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String linkableUserId = findLinkableUserId(normalizedEmail);
        if (linkableUserId != null) {
            return linkToExistingAccount(linkableUserId, normalizedEmail, rawPassword);
        }

        LocalAccount account = new LocalAccount();
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        // 최종 userId(local@{id})는 PK 가 정해져야 알 수 있다.
        // NOT NULL/UNIQUE 제약을 지키기 위해 임시값으로 INSERT 한 뒤 확정한다.
        // (이메일을 임시값에 쓰면 user_id VARCHAR(100) 을 넘길 수 있어 길이가 고정된 UUID 를 쓴다)
        account.setUserId(LocalAccount.USER_ID_PREFIX + "tmp:" + UUID.randomUUID());
        try {
            account = localAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 확인과 INSERT 사이에 같은 이메일이 들어온 경우(UNIQUE 위반)
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        account.setUserId(LocalAccount.USER_ID_PREFIX + account.getId());
        account = localAccountRepository.save(account);

        // 카카오 로그인과 공유하는 user 테이블에 프로필 행 생성
        String resolvedNickname = resolveNickname(nickname, normalizedEmail);
        userRepository.save(UserVo.builder()
            .userId(account.getUserId())
            .username(resolvedNickname)
            .email(normalizedEmail)
            .build());

        log.info("[LocalAuth] 회원가입 userId={}", account.getUserId());
        return issueTokens(account.getUserId(), resolvedNickname);
    }

    /**
     * 로그인. 이메일/비밀번호 검증 후 카카오 로그인과 동일한 형태의 토큰을 발급한다.
     * 실패 사유(이메일 없음/비밀번호 불일치)는 구분해 알려주지 않는다.
     */
    public AuthResult login(String email, String rawPassword) {
        if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("이메일과 비밀번호를 입력해주세요.");
        }

        Optional<LocalAccount> found = localAccountRepository.findByEmail(normalizeEmail(email));
        if (found.isEmpty() || !passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        LocalAccount account = found.get();
        UserVo user = userRepository.findByUserId(account.getUserId());
        String nickname = user != null ? user.getUsername() : localPart(account.getEmail());

        return issueTokens(account.getUserId(), nickname);
    }

    /**
     * 같은 이메일로 이미 쓰던 계정의 userId. 없으면 null.
     *
     * <p>local@ 로 시작하는 userId 는 자체 가입으로 만들어진 것이라 제외한다
     * (같은 이메일이면 위쪽 existsByEmail 에서 이미 걸리고, 남아 있다면 정리 안 된 옛 데이터다).
     */
    private String findLinkableUserId(String email) {
        List<UserVo> sameEmailUsers = userProfileRepository.findByEmailOrderByIdAsc(email);
        for (UserVo user : sameEmailUsers) {
            String userId = user.getUserId();
            if (userId != null && !userId.startsWith(LocalAccount.USER_ID_PREFIX)) {
                return userId;
            }
        }
        return null;
    }

    /**
     * 기존 계정에 이메일+비밀번호 자격증명만 추가한다. 프로필(user 행)은 그대로 두고 새로 만들지 않는다.
     */
    private AuthResult linkToExistingAccount(String userId, String normalizedEmail, String rawPassword) {
        LocalAccount account = new LocalAccount();
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setUserId(userId);
        try {
            localAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일 또는 같은 userId 로 동시에 가입 요청이 들어온 경우(UNIQUE 위반)
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        UserVo user = userRepository.findByUserId(userId);
        String nickname = user != null && user.getUsername() != null && !user.getUsername().isBlank()
            ? user.getUsername()
            : localPart(normalizedEmail);

        log.info("[LocalAuth] 기존 계정에 자체 로그인 연동 userId={}", userId);
        return issueTokens(userId, nickname);
    }

    /** 회원가입 화면의 중복 확인용 */
    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return localAccountRepository.existsByEmail(email.trim().toLowerCase());
    }

    /** access/refresh 토큰 발급 + refresh 저장. 카카오 로그인과 같은 흐름. */
    private AuthResult issueTokens(String userId, String nickname) {
        String accessToken = utilityNklcb.generateToken(userId, false);
        String refreshToken = utilityNklcb.generateToken(userId, true);
        tokenService.saveRefreshToken(userId, refreshToken);
        return new AuthResult(accessToken, refreshToken, userId, nickname);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        }
        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 " + MIN_PASSWORD_LENGTH + "~" + MAX_PASSWORD_LENGTH + "자로 입력해주세요.");
        }
    }

    private String resolveNickname(String nickname, String email) {
        String name = nickname == null ? null : nickname.trim();
        if (name == null || name.isEmpty()) {
            name = localPart(email);
        }
        if (name.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("닉네임은 " + MAX_NICKNAME_LENGTH + "자 이하로 입력해주세요.");
        }
        return name;
    }

    /** 이메일의 @ 앞부분 */
    private String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** 로그인/회원가입 결과. 응답 형태는 kakaoLogin 과 맞춘다. */
    public record AuthResult(String token, String refreshToken, String userId, String nickname) {
    }
}
