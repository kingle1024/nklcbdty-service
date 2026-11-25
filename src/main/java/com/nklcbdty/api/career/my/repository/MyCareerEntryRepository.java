package com.nklcbdty.api.career.my.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.career.my.vo.MyCareerEntry;

public interface MyCareerEntryRepository extends JpaRepository<MyCareerEntry, Long> {

    /**
     * 한 사용자의 전부, 최근 것부터. 정렬 규칙은 관리자 경력과 같다 — 진행 중(종료일 없음)이 맨 위,
     * 그다음 시작일 역순, 시작일이 없는 항목(자격증 등)은 맨 뒤.
     */
    @Query("select e from MyCareerEntry e where e.userId = :userId "
         + "order by case when e.startedOn is null then 2 "
         + "              when e.endedOn is null then 0 else 1 end, "
         + "         e.startedOn desc, e.id desc")
    List<MyCareerEntry> findAllRecentFirst(@Param("userId") String userId);

    Optional<MyCareerEntry> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);
}
