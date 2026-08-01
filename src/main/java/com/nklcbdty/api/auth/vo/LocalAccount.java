package com.nklcbdty.api.auth.vo;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 이메일+비밀번호 회원 계정(자체 회원가입).
 * 프로필(닉네임/이메일)은 카카오 로그인과 공유하는 user 테이블(UserVo)에 있고,
 * 이 테이블은 로그인 자격증명(이메일, 비밀번호 해시)과 user.user_id 연결만 담당한다.
 * 카카오 사용자의 userId 가 "kakao@{id}" 이듯 자체 가입 사용자는 "local@{id}" 를 쓴다.
 */
@Entity
@Table(name = "local_account")
@Data
public class LocalAccount {

    /** userId 접두어. 최종 userId = local@{id} */
    public static final String USER_ID_PREFIX = "local@";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디로 쓰는 이메일 */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt 해시 */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** user 테이블과 연결되는 값. "local@{id}" */
    @Column(nullable = false, unique = true, length = 100)
    private String userId;

    @CreationTimestamp
    private LocalDateTime insertDts;
}
