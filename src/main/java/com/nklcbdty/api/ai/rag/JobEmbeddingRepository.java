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
     *
     * <p>마감된 공고는 뺀다. 공고의 87%가 마감분이라(2026-11-09: 5,354건 중 열린 공고 690건) 이것까지 색인하면
     * Gemini 무료 한도(하루 약 1,000건)를 마감 공고에 다 쓴다. {@code end_date} 는 "yyyy-MM-dd HH:mm[:ss]"
     * 문자열이라 사전순 비교가 곧 날짜 비교다. 링크 점검 실패 표시(2000-01-01)도 여기서 빠진다.
     * {@code now} 는 "yyyy-MM-dd HH:mm:ss" 로 넘긴다.</p>
     */
    @Query("SELECT j FROM Job_mst j "
         + "WHERE (j.embedding IS NULL OR j.embeddingVersion IS NULL OR j.embeddingVersion <> :version) "
         + "AND (j.endDate IS NULL OR j.endDate IN ('영입종료시', 'error') OR j.endDate >= :now)")
    List<Job_mst> findStale(@Param("version") String version, @Param("now") String now, Pageable pageable);

    /**
     * 캐시 워밍업용. 지금 쓰는 모델 버전의 (id, embedding) 만 가볍게 가져옴.
     * <p>리턴 타입은 Object[] 배열: [0]=Long id, [1]=byte[] embedding</p>
     */
    @Query("SELECT j.id, j.embedding FROM Job_mst j "
         + "WHERE j.embedding IS NOT NULL AND j.embeddingVersion = :version")
    List<Object[]> findEmbeddingsRaw(@Param("version") String version);

    /**
     * 키워드 대체 매칭({@link KeywordJobMatcher})용. 공고 전체를 엔티티로 올리지 않고 필요한 칸만 읽는다.
     * <p>[0]=Long id, [1]=annoSubject, [2]=subJobCdNm, [3]=classCdNm, [4]=sysCompanyCdNm, [5]=workplace, [6]=endDate</p>
     */
    @Query("SELECT j.id, j.annoSubject, j.subJobCdNm, j.classCdNm, j.sysCompanyCdNm, j.workplace, j.endDate "
         + "FROM Job_mst j")
    List<Object[]> findKeywordRows();
}
