package com.nklcbdty.api.ai.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 자연어 공고 검색 엔드포인트.
 *
 * <p>예: {@code GET /api/jobs/search?q=원격 가능한 백엔드 시니어&k=10}</p>
 */
@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final SemanticSearchService search;

    public JobSearchController(SemanticSearchService search) {
        this.search = search;
    }

    public static class JobSearchHit {
        public Long id;
        public String companyCd;
        public String annoSubject;
        public String classCdNm;
        public String subJobCdNm;
        public String sysCompanyCdNm;
        public String workplace;
        public String jobDetailLink;
        public long personalHistory;
        public long personalHistoryEnd;
        public float score;

        /** 의미 검색 결과 → 응답 DTO 매핑. (PDF 매칭 등 다른 진입점과 공유) */
        public static JobSearchHit from(SemanticSearchService.Result r) {
            JobSearchHit h = new JobSearchHit();
            h.id = r.job.getId();
            h.companyCd = r.job.getCompanyCd();
            h.annoSubject = r.job.getAnnoSubject();
            h.classCdNm = r.job.getClassCdNm();
            h.subJobCdNm = r.job.getSubJobCdNm();
            h.sysCompanyCdNm = r.job.getSysCompanyCdNm();
            h.workplace = r.job.getWorkplace();
            h.jobDetailLink = r.job.getJobDetailLink();
            h.personalHistory = r.job.getPersonalHistory();
            h.personalHistoryEnd = r.job.getPersonalHistoryEnd();
            h.score = r.score;
            return h;
        }
    }

    @GetMapping("/search")
    public List<JobSearchHit> search(@RequestParam("q") String q,
                                     @RequestParam(value = "k", defaultValue = "10") int k) {
        List<SemanticSearchService.Result> results = search.search(q, k);
        List<JobSearchHit> out = new ArrayList<>(results.size());
        for (SemanticSearchService.Result r : results) {
            out.add(JobSearchHit.from(r));
        }
        return out;
    }
}
