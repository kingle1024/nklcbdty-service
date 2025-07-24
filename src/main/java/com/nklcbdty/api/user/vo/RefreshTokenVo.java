package com.nklcbdty.api.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime; // 날짜와 시간 관리를 위한 클래스

import org.hibernate.annotations.Comment;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenVo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("리프레시 토큰 레코드의 고유 ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("이 토큰이 속한 사용자 ID (Users 테이블의 PK)")
    private String userId;

    @Column(name = "refresh_token", nullable = false, length = 500)
    @Comment("해싱된 리프레시 토큰 값. 실제 리프레시 토큰 문자열을 그대로 저장하지 않고 해싱한 값을 저장.")
    private String token;

    @Column(name = "issued_at", nullable = false)
    @Comment("토큰 발급 시각")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    @Comment("토큰 만료 시각. 이 시각이 지나면 더 이상 유효하지 않음.")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    @Comment("토큰이 수동으로 무효화(블랙리스트 추가 등)된 시각. 로그아웃 시 여기에 값을 넣어주면 됨.")
    private LocalDateTime revokedAt;

    @Column(name = "is_revoked", nullable = false)
    @Comment("토큰이 무효화되었는지 여부 (TRUE = 무효화됨).")
    private boolean isRevoked;

    @Column(name = "ip_address", length = 45)
    @Comment("이 토큰 발급 시 클라이언트의 IP 주소.")
    private String ipAddress;

    @Column(name = "user_agent")
    @Comment("이 토큰 발급 시 클라이언트의 User-Agent 정보.")
    private String userAgent;
}
