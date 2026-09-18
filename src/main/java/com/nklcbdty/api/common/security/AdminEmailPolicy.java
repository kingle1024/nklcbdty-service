package com.nklcbdty.api.common.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 어떤 이메일을 관리자로 볼지 정한다.
 *
 * admin_account(아이디/비밀번호) 로그인과는 별개로, 여기 등록된 이메일로 평소처럼 로그인하면
 * 그 사람의 access 토큰에 role=ADMIN 이 실려 /api/admin/** 을 바로 쓸 수 있다.
 * (프론트는 로그인 응답의 isAdmin 을 보고 헤더에 관리자 메뉴를 띄운다)
 *
 * 목록은 admin.emails 프로퍼티(쉼표 구분)로 바꾼다.
 */
@Slf4j
@Component
public class AdminEmailPolicy {

    private final Set<String> adminEmails;
    private final UserRepository userRepository;

    public AdminEmailPolicy(@Value("${admin.emails:teran1024@naver.com}") String adminEmails,
                            UserRepository userRepository) {
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                                 .map(AdminEmailPolicy::normalize)
                                 .filter(email -> !email.isEmpty())
                                 .collect(Collectors.toUnmodifiableSet());
        this.userRepository = userRepository;
    }

    /** 로그인·회원가입처럼 이메일을 손에 들고 있는 곳에서 쓴다. */
    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(normalize(email));
    }

    /**
     * 토큰 갱신처럼 이메일을 받지 못하는 곳에서 쓴다. 관리자면 표시용 이름, 아니면 null.
     * user 테이블의 이메일로 다시 판정하므로 로그인 때 이메일을 채워 둬야 갱신 후에도 관리자로 남는다.
     */
    public String adminNameByUserId(String userId) {
        if (userId == null || adminEmails.isEmpty()) {
            return null;
        }
        try {
            UserVo user = userRepository.findByUserId(userId);
            if (user == null || !isAdminEmail(user.getEmail())) {
                return null;
            }
            return user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : userId;
        } catch (Exception e) {
            // 관리자 판정에 실패해도 일반 사용자로 계속 쓸 수 있어야 한다.
            log.warn("[AdminEmailPolicy] 관리자 판정 실패 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
