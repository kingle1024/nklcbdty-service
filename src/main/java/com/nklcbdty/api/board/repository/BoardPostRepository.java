package com.nklcbdty.api.board.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.board.vo.BoardPost;
import com.nklcbdty.api.board.vo.BoardType;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    /** 게시판별 목록(삭제 글 제외) */
    Page<BoardPost> findByBoardTypeAndDeletedFalse(BoardType boardType, Pageable pageable);

    /** 제목/내용/작성자 검색(삭제 글 제외) */
    @Query("select p from BoardPost p "
         + "where p.boardType = :boardType and p.deleted = false "
         + "  and (lower(p.title) like lower(concat('%', :keyword, '%')) "
         + "    or lower(p.content) like lower(concat('%', :keyword, '%')) "
         + "    or lower(p.authorName) like lower(concat('%', :keyword, '%')))")
    Page<BoardPost> search(@Param("boardType") BoardType boardType,
                           @Param("keyword") String keyword,
                           Pageable pageable);

    /** 삭제되지 않은 글 1건 */
    Optional<BoardPost> findByIdAndDeletedFalse(Long id);

    /**
     * 조회수 +1. 동시 조회에도 값이 유실되지 않도록 엔티티 수정 대신 UPDATE 로 증가시킨다.
     * 영속성 컨텍스트에는 반영되지 않으므로 응답 값은 호출부에서 +1 해 준다.
     */
    @Modifying
    @Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void increaseViewCount(@Param("id") Long id);
}
