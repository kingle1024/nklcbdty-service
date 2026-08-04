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
 * 자유게시판 댓글. 글(BoardPost)과는 FK 없이 postId 로만 연결한다(기존 테이블들과 같은 방식).
 * 삭제는 deleted 플래그로 처리한다.
 */
@Entity
@Table(name = "board_comment")
@Data
public class BoardComment {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    /** 작성자 userId */
    @Column(nullable = false, length = 100)
    private String authorId;

    /** 작성 시점의 닉네임 스냅샷 */
    @Column(nullable = true, length = 100)
    private String authorName;

    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
