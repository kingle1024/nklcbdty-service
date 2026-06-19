package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobEmbeddingRepository extends JpaRepository<Job_mst, Long> {

    /** 아직 임베딩이 없는 공고를 페이지 단위로 조회 (배치 인덱싱용) */
    @Query("SELECT j FROM Job_mst j WHERE j.embedding IS NULL")
    List<Job_mst> findUnindexed(Pageable pageable);

    /**
     * 캐시 워밍업용. (id, embedding) 만 가볍게 가져옴.
     * <p>리턴 타입은 Object[] 배열: [0]=Long id, [1]=byte[] embedding</p>
     */
    @Query("SELECT j.id, j.embedding FROM Job_mst j WHERE j.embedding IS NOT NULL")
    List<Object[]> findAllEmbeddingsRaw();
}
