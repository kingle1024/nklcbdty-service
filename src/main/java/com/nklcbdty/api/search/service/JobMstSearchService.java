package com.nklcbdty.api.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.List;

import com.nklcbdty.api.search.dto.JobSearchDto;
import com.nklcbdty.api.search.model.JobMstDocument;

@Service
@RequiredArgsConstructor
public class JobMstSearchService {

    private final ElasticsearchOperations operations;

    public Page<SearchHit<JobMstDocument>> search(String keyword, int page, int size) {

        // 1) 하이라이트 파라미터(프리/포스트 태그 등)
        HighlightParameters highlightParams = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                // 필요 시 조각 옵션 예시:
                // .withFragmentSize(150)
                // .withNumberOfFragments(1)
                .build();

        // 2) 하이라이트 필드 지정
        List<HighlightField> highlightFields = List.of(
                new HighlightField("annoSubject"),
                new HighlightField("subJobCdNm")
        );

        // 3) 스프링 하이라이트 객체 생성(Parameters + Fields)
        Highlight highlight = new Highlight(highlightParams, highlightFields);

        // 4) 쿼리 구성: 멀티매치(공고명 가중치 2), 오타 허용, AND 연산자, 페이징, 하이라이트
        var query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("annoSubject^2", "subJobCdNm")
                        .operator(Operator.And)
                        .fuzziness("AUTO")
                ))
                .withPageable(PageRequest.of(page, size))
                .withHighlightQuery(new HighlightQuery(highlight, JobMstDocument.class))
                .build();

        // 5) 실행
        SearchHits<JobMstDocument> hits = operations.search(query, JobMstDocument.class);

        // 6) Page로 변환(총 개수 포함)
        List<SearchHit<JobMstDocument>> content = hits.getSearchHits();
        long total = hits.getTotalHits();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    public List<JobSearchDto> searchAsDto(String keyword, int page, int size) {
        Page<SearchHit<JobMstDocument>> result = search(keyword, page, size);

        return result.getContent().stream().map(hit -> {
            var doc = hit.getContent();
            var hl = hit.getHighlightFields();

            String annoSubjectHl = hl.getOrDefault("annoSubject", List.of())
                                     .stream().findFirst().orElse(null);
            String subJobCdNmHl = hl.getOrDefault("subJobCdNm", List.of())
                                     .stream().findFirst().orElse(null);

            return JobSearchDto.builder()
                .id(doc.getId())
                .companyCd(doc.getCompanyCd())
                .annoId(doc.getAnnoId())
                .classCdNm(doc.getClassCdNm())
                .empTypeCdNm(doc.getEmpTypeCdNm())
                .annoSubject(doc.getAnnoSubject())
                .subJobCdNm(doc.getSubJobCdNm())
                .sysCompanyCdNm(doc.getSysCompanyCdNm())
                .jobDetailLink(doc.getJobDetailLink())
                .endDate(doc.getEndDate())
                .personalHistory(doc.getPersonalHistory())
                .personalHistoryEnd(doc.getPersonalHistoryEnd())
                .insertDts(doc.getInsertDts())
                .annoSubjectHl(annoSubjectHl)
                .subJobCdNmHl(subJobCdNmHl)
                .build();
        }).toList();
    }
}
