package com.nklcbdty.api.crawler.content;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nklcbdty.common.vo.Job_mst;

@Repository
public interface JobContentRepository extends JpaRepository<JobContent, Long> {

    /**
     * 본문 수집이 필요한 공고를 오래된 것부터 준다.
     *
     * <p>대상은 (1) 아직 한 번도 시도 안 한 공고, (2) 마지막 시도가 {@code before} 이전인 공고다.
     * 실패한 공고도 (2) 로 다시 잡히지만 재시도 간격만큼은 쉬어간다 — 죽은 링크를 매분 때리지 않는다.
     * 살아있는 공고(마감 전)만 본다. 이미 끝난 공고의 본문을 이제 와 긁을 이유가 없다.</p>
     */
    @Query("""
        SELECT j FROM Job_mst j
         WHERE j.subJobCdNm IS NOT NULL
           AND j.jobDetailLink IS NOT NULL AND j.jobDetailLink <> ''
           AND NOT EXISTS (
                 SELECT 1 FROM JobContent c
                  WHERE c.jobId = j.id AND c.fetchedAt > :before
               )
         ORDER BY j.id DESC
        """)
    List<Job_mst> findNeedingContent(@Param("before") LocalDateTime before, Pageable pageable);

    long countByContentIsNotNull();
}
