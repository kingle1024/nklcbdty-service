package com.nklcbdty.api.career.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nklcbdty.api.career.vo.CareerEntry;

public interface CareerEntryRepository extends JpaRepository<CareerEntry, Long> {

    /**
     * 전부, 최근 것부터. 진행 중(종료일 없음)인 항목을 맨 위에 두고, 그다음은 시작일 역순이다.
     * 시작일이 없는 항목(날짜를 안 적은 자격증 등)은 맨 뒤로 보낸다.
     * 날짜가 같은 항목은 id 로 순서를 못박는다.
     */
    @Query("select e from CareerEntry e "
         + "order by case when e.startedOn is null then 2 "
         + "              when e.endedOn is null then 0 else 1 end, "
         + "         e.startedOn desc, e.id desc")
    List<CareerEntry> findAllRecentFirst();

    boolean existsBySlug(String slug);
}
