package com.nklcbdty.api.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.board.vo.BoardComment;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    /** 특정 글의 댓글 목록(오래된 순) */
    List<BoardComment> findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(Long postId);

    /** 삭제되지 않은 댓글 1건 */
    Optional<BoardComment> findByIdAndDeletedFalse(Long id);

    /** 목록 화면용: 글 여러 건의 댓글 수를 한 번에 조회. 반환 행은 [postId, count] */
    @Query("select c.postId, count(c) from BoardComment c "
         + "where c.postId in :postIds and c.deleted = false group by c.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 글이 삭제될 때 딸린 댓글도 함께 감춘다 */
    @Modifying
    @Query("update BoardComment c set c.deleted = true where c.postId = :postId and c.deleted = false")
    int softDeleteByPostId(@Param("postId") Long postId);
}
