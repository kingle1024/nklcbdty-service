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
 * 자유게시판 글. 삭제는 실제 DELETE 대신 deleted 플래그로 처리한다(댓글이 딸려 있어 흔적을 남긴다).
 * 작성자 닉네임(authorName)은 작성 시점 값을 복사해 둔다 — 나중에 닉네임이 바뀌어도 글 목록 조회에
 * user 테이블을 조인하지 않도록.
 */
@Entity
@Table(name = "board_post")
@Data
public class BoardPost {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_CONTENT_LENGTH = 10000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 작성자 userId. "kakao@{id}" 또는 "local@{id}" */
    @Column(nullable = false, length = 100)
    private String authorId;

    /** 작성 시점의 닉네임 스냅샷 */
    @Column(nullable = true, length = 100)
    private String authorName;

    @Column(nullable = false)
    private int viewCount;

    /** 목록에서 "제목 [3]" 처럼 보여주기 위한 댓글 수. 댓글 등록/삭제 시 함께 갱신한다. */
    @Column(nullable = false)
    private int commentCount;

    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
