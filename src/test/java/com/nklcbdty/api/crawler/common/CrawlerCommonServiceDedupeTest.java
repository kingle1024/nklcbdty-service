package com.nklcbdty.api.crawler.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.ai.nlp.PersonalHistoryEnsemble;
import com.nklcbdty.api.ai.service.GeminiService;
import com.nklcbdty.common.crawler.repository.CrawlerRepository;
import com.nklcbdty.common.vo.Job_mst;

// 한 크롤 결과 안에 같은 annoId 가 섞여 들어오면 저장 전에 접혀야 한다.
// 접지 않으면 anno_id 에 유니크 제약이 없어 같은 공고가 job_mst 에 두 행으로 남는다.
@ExtendWith(MockitoExtension.class)
class CrawlerCommonServiceDedupeTest {

    @Mock
    private CrawlerRepository crawlerRepository;
    @Mock
    private GeminiService geminiService;
    @Mock
    private PersonalHistoryEnsemble personalHistoryEnsemble;

    private CrawlerCommonService service() {
        return new CrawlerCommonService(crawlerRepository, geminiService, personalHistoryEnsemble);
    }

    private Job_mst job(String annoId, String subject) {
        Job_mst j = new Job_mst();
        j.setAnnoId(annoId);
        j.setAnnoSubject(subject);
        return j;
    }

    @Test
    void 같은_annoId_가_두번_들어오면_먼저_나온_행만_남는다() {
        Job_mst first = job("30005174", "Frontend Developer");
        Job_mst duplicate = job("30005174", "Frontend Developer");
        Job_mst other = job("30005187", "Backend Developer");

        List<Job_mst> result = service()
            .dedupeCrawledByAnnoId("NAVER", Arrays.asList(first, duplicate, other));

        assertThat(result).containsExactly(first, other);
    }

    @Test
    void annoId_가_없는_행은_접지_않는다() {
        Job_mst noId1 = job(null, "제목 A");
        Job_mst noId2 = job(null, "제목 B");

        List<Job_mst> result = service().dedupeCrawledByAnnoId("KAKAO", Arrays.asList(noId1, noId2));

        assertThat(result).containsExactly(noId1, noId2);
    }

    @Test
    void 중복이_없으면_원본_순서와_내용이_그대로다() {
        Job_mst a = job("1", "A");
        Job_mst b = job("2", "B");
        Job_mst c = job("3", "C");

        List<Job_mst> result = service().dedupeCrawledByAnnoId("LINE", Arrays.asList(a, b, c));

        assertThat(result).containsExactly(a, b, c);
    }
}
