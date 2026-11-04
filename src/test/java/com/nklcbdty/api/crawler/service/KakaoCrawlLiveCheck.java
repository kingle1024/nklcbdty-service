package com.nklcbdty.api.crawler.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.vo.Job_mst;

/**
 * 카카오 계열사 전체 크롤을 실제로 돌려 계열사별로 몇 건이 잡히고 직무 분류가 붙는지 본다.
 * DB·LLM 은 쓰지 않는다(getNotSaveJobItem 은 받은 목록을 그대로 돌려준다).
 *
 * <p>단위 테스트가 아니다. {@code ./gradlew test --tests "*KakaoCrawlLiveCheck*"} 로 켜서 확인한다.</p>
 */
@org.junit.jupiter.api.Disabled("실제 채용 사이트를 호출한다. 필요할 때 수동으로 켠다")
class KakaoCrawlLiveCheck {

    @Test
    void crawlAll() throws Exception {
        CrawlerCommonService common = spy(new CrawlerCommonService(null, null, null));
        doAnswer(inv -> inv.getArgument(1)).when(common).getNotSaveJobItem(anyString(), any());
        doAnswer(inv -> new PersonalHistoryDto()).when(common).getPersonalHistory(any());

        List<Job_mst> jobs = new KakaoCrawlerService(common).crawlJobs().get();

        Map<String, int[]> byCompany = new TreeMap<>();
        StringBuilder rows = new StringBuilder();
        for (Job_mst job : jobs) {
            int[] counts = byCompany.computeIfAbsent(job.getSysCompanyCdNm(), k -> new int[2]);
            counts[0]++;
            if (job.getSubJobCdNm() != null) {
                counts[1]++;
            }
            rows.append(String.format("%s | %s | %s | %s | %s%n",
                job.getSysCompanyCdNm(), job.getSubJobCdNm(), job.getAnnoSubject(), job.getEndDate(), job.getJobDetailLink()));
        }

        StringBuilder report = new StringBuilder("카카오 전체 " + jobs.size() + "건 (계열사: 전체/분류됨)\n");
        byCompany.forEach((name, c) -> report.append(String.format("  %s: %d / %d%n", name, c[0], c[1])));
        report.append('\n').append(rows);

        // 윈도우 콘솔 코드페이지에서 한글이 깨져 결과를 UTF-8 파일로 남긴다.
        Path out = Path.of("build", "kakao-crawl-live-check.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println("결과 파일: " + out.toAbsolutePath());
    }
}
