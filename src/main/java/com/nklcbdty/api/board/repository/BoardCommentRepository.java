package com.nklcbdty.api.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.board.vo.BoardComment;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    /** 특정 글의 댓글(등록순) */
    List<BoardComment> findByPostIdAndDeletedFalseOrderByIdAsc(Long postId);

    Optional<BoardComment> findByIdAndDeletedFalse(Long id);

    int countByPostIdAndDeletedFalse(Long postId);
}
