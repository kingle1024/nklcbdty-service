package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobEmbeddingRepository extends JpaRepository<Job_mst, Long> {

    /**
     * 임베딩이 없거나 지금 쓰는 모델 버전이 아닌 공고를 페이지 단위로 조회 (배치 인덱싱용).
     *
     * <p>공급자나 차원이 바뀌면 벡터 차원도 달라진다. 옛 차원 벡터는 질의 벡터와 길이가 맞지 않아
     * 검색에서 조용히 무시되므로(=그 공고가 영구히 안 잡힘), 버전이 다른 건 재인덱싱 대상으로 잡는다.</p>
     */
    @Query("SELECT j FROM Job_mst j "
         + "WHERE j.embedding IS NULL OR j.embeddingVersion IS NULL OR j.embeddingVersion <> :version")
    List<Job_mst> findStale(@Param("version") String version, Pageable pageable);

    /**
     * 캐시 워밍업용. 지금 쓰는 모델 버전의 (id, embedding) 만 가볍게 가져옴.
     * <p>리턴 타입은 Object[] 배열: [0]=Long id, [1]=byte[] embedding</p>
     */
    @Query("SELECT j.id, j.embedding FROM Job_mst j "
         + "WHERE j.embedding IS NOT NULL AND j.embeddingVersion = :version")
    List<Object[]> findEmbeddingsRaw(@Param("version") String version);
}
