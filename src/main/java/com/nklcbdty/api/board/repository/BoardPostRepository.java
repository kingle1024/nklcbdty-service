package com.nklcbdty.api.board.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.board.vo.BoardPost;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    /** 목록(최신순). 삭제된 글은 제외한다. */
    Page<BoardPost> findByDeletedFalseOrderByIdDesc(Pageable pageable);

    /** 제목/본문 검색 */
    @Query("select p from BoardPost p where p.deleted = false "
        + "and (lower(p.title) like lower(concat('%', :keyword, '%')) "
        + "  or lower(p.content) like lower(concat('%', :keyword, '%'))) "
        + "order by p.id desc")
    Page<BoardPost> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Optional<BoardPost> findByIdAndDeletedFalse(Long id);

    /**
     * 조회수 증가. 엔티티를 읽어 save 하면 @UpdateTimestamp 때문에 수정일시가 바뀌므로
     * 조회수만 UPDATE 한다. 삭제된 글은 올리지 않는다.
     */
    @Modifying
    @Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.id = :id and p.deleted = false")
    void increaseViewCount(@Param("id") Long id);

    /** 댓글 수 재계산 결과 반영 (댓글 등록/삭제 후) */
    @Modifying
    @Query("update BoardPost p set p.commentCount = :count where p.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("count") int count);
}
