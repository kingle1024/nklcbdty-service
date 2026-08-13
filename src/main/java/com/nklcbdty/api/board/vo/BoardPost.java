package com.nklcbdty.api.board.vo;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 게시판 글. 공지사항(NOTICE)과 자유게시판(FREE)이 한 테이블을 board_type 으로 구분해 함께 쓴다.
 * 삭제는 실제 삭제가 아니라 deleted 플래그(soft delete)로 처리한다.
 */
@Entity
@Table(name = "board_post")
@Data
public class BoardPost implements OwnedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoardType boardType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 로그인 사용자 id. 익명 글/관리자 글이면 null */
    @Column(nullable = true)
    private String authorId;

    /** 목록에 노출할 작성자 표시명 */
    @Column(nullable = false, length = 50)
    private String authorName;

    /** 익명 글 수정·삭제용 BCrypt 해시. 로그인 글/관리자 글이면 null */
    @Column(nullable = true)
    private String passwordHash;

    /** 관리자가 작성한 경우 그 관리자 계정(감사용) */
    @Column(nullable = true, length = 100)
    private String adminAuthor;

    @Column(nullable = true, length = 64)
    private String authorIp;

    @Column(nullable = false)
    private int viewCount;

    /** 목록 상단 고정. 관리자만 설정할 수 있다. */
    @Column(nullable = false)
    private boolean pinned;

    /** soft delete 플래그 */
    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
