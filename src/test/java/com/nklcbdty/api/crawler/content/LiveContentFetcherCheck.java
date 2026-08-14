package com.nklcbdty.api.crawler.content;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.content.fetcher.BaeminContentFetcher;
import com.nklcbdty.api.crawler.content.fetcher.GenericHtmlContentFetcher;
import com.nklcbdty.api.crawler.content.fetcher.GreenhouseContentFetcher;
import com.nklcbdty.api.crawler.content.fetcher.LineContentFetcher;
import com.nklcbdty.api.crawler.content.fetcher.NaverContentFetcher;
import com.nklcbdty.api.crawler.content.fetcher.TossContentFetcher;
import com.nklcbdty.common.vo.Job_mst;

/**
 * 실제 채용 사이트를 호출해 수집기가 본문을 가져오는지 눈으로 확인하는 용도.
 *
 * <p>단위 테스트가 아니다(외부 의존·비결정적). 채용 사이트 마크업이 바뀌어 본문이 안 잡힐 때
 * {@code ./gradlew test --tests "*LiveContentFetcherCheck*"} 로 켜서 회사별로 어디가 깨졌는지 본다.
 * 평소에는 {@code @Disabled} 로 꺼 둔다.</p>
 */
@org.junit.jupiter.api.Disabled("실제 채용 사이트를 호출한다. 필요할 때 수동으로 켠다")
class LiveContentFetcherCheck {

    private final CrawlerCommonService common = new CrawlerCommonService(null, null, null);

    private Job_mst job(String companyCd, String annoId, String link) {
        Job_mst job = new Job_mst();
        job.setId(1L);
        job.setCompanyCd(companyCd);
        job.setAnnoId(annoId);
        job.setJobDetailLink(link);
        return job;
    }

    // 윈도우 콘솔 코드페이지에서 한글이 깨져 결과를 UTF-8 파일로 남긴다.
    private final StringBuilder report = new StringBuilder();

    private void check(JobContentFetcher fetcher, Job_mst job) {
        try {
            JobContentFetcher.Fetched fetched = fetcher.fetch(job);
            if (fetched == null || fetched.isEmpty()) {
                report.append(String.format("  %-18s %-8s FAIL 본문 없음%n", fetcher.sourceName(), job.getCompanyCd()));
                return;
            }
            String text = fetched.text().strip();
            report.append(String.format("  %-18s %-8s %s %d자 | %s%n",
                fetcher.sourceName(), job.getCompanyCd(),
                ContentText.isMeaningful(text) ? "OK  " : "SHORT",
                text.length(),
                text.replace("\n", " / ").substring(0, Math.min(90, text.length()))));
        } catch (Exception e) {
            report.append(String.format("  %-18s %-8s FAIL %s%n", fetcher.sourceName(), job.getCompanyCd(), e.getMessage()));
        }
    }

    private void writeReport() {
        try {
            java.nio.file.Path out = java.nio.file.Path.of("build", "live-content-check.txt");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, report.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("리포트 기록 실패", e);
        }
    }

    @Test
    void 회사별_본문_수집_확인() {
        report.append("=== 공고 본문 수집 실측 ===\n");

        check(new BaeminContentFetcher(common),
            job("BAEMIN", "25163", "https://career.woowahan.com/recruitment/R2605009/detail"));

        check(new GreenhouseContentFetcher(common),
            job("DAANGN", "6045408003", "https://careers.daangn.com/jobs/role/6045408003/"));

        check(new GreenhouseContentFetcher(common),
            job("COUPANG", "6137112", "https://www.coupang.jobs/kr/jobs/6137112/x/?gh_jid=6137112"));

        check(new TossContentFetcher(common),
            job("TOSS", "6619423003", "https://toss.im/career/job-detail?gh_jid=6619423003"));

        check(new LineContentFetcher(common),
            job("LINE", "3049", "https://careers.linecorp.com/ko/jobs/3049"));

        check(new NaverContentFetcher(common),
            job("NAVER", "30005210", "https://recruit.navercorp.com/rcrt/view.do?annoId=30005210"));

        GenericHtmlContentFetcher generic = new GenericHtmlContentFetcher(common);
        for (Job_mst kakao : List.of(
            job("KAKAO", "257469", "https://kakaobank.recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2&jobnoticeSn=257469"),
            job("YANOLJA", "1", "https://careers.yanolja.co/"))) {
            check(generic, kakao);
        }

        writeReport();
    }
}
