package com.nklcbdty.api.board.vo;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 게시판 댓글. 공지사항/자유게시판 양쪽 글에 모두 달 수 있다.
 * 게시글과 마찬가지로 deleted 플래그로 soft delete 한다.
 */
@Entity
@Table(name = "board_comment")
@Data
public class BoardComment implements OwnedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 게시글(board_post) PK */
    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 로그인 사용자 id. 익명 댓글/관리자 댓글이면 null */
    @Column(nullable = true)
    private String authorId;

    @Column(nullable = false, length = 50)
    private String authorName;

    /** 익명 댓글 수정·삭제용 BCrypt 해시 */
    @Column(nullable = true)
    private String passwordHash;

    /** 관리자가 작성한 경우 그 관리자 계정(감사용) */
    @Column(nullable = true, length = 100)
    private String adminAuthor;

    @Column(nullable = true, length = 64)
    private String authorIp;

    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
