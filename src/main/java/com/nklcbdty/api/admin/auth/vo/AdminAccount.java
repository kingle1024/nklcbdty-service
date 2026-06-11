package com.nklcbdty.api.admin.auth.vo;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 관리자 계정. 관리자 페이지 로그인에 사용한다. */
@Entity
@Table(name = "admin_account")
@Data
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** BCrypt 해시 */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = true, length = 100)
    private String displayName;

    @CreationTimestamp
    private LocalDateTime insertDts;
}
