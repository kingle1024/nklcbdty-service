package com.nklcbdty.api.crawler.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.common.vo.Job_mst;

/**
 * 카카오모빌리티 채용 사이트(greetinghr)를 실제로 불러 공고가 몇 건 잡히는지 눈으로 확인하는 용도.
 *
 * <p>단위 테스트가 아니다(외부 의존·비결정적). 사이트 구조가 바뀌어 공고가 0건이 될 때
 * {@code ./gradlew test --tests "*KakaoMobilityLiveCheck*"} 로 켜서 확인한다.
 * 평소에는 {@code @Disabled} 로 꺼 둔다.</p>
 */
@org.junit.jupiter.api.Disabled("실제 채용 사이트를 호출한다. 필요할 때 수동으로 켠다")
class KakaoMobilityLiveCheck {

    @Test
    void crawlMobility() throws IOException {
        CrawlerCommonService common = new CrawlerCommonService(null, null, null);
        KakaoCrawlerService service = new KakaoCrawlerService(common);

        String nextData = common.jsoupConnect("https://kakaomobility.career.greetinghr.com/ko/guide")
            .get()
            .getElementById("__NEXT_DATA__")
            .html();

        List<Job_mst> jobs = new ArrayList<>();
        service.parseMobilityOpenings(nextData, jobs);

        // 윈도우 콘솔 코드페이지에서 한글이 깨져 결과를 UTF-8 파일로 남긴다.
        StringBuilder report = new StringBuilder("카카오모빌리티 공고 " + jobs.size() + "건\n");
        for (Job_mst job : jobs) {
            report.append(String.format("%s | %s | %s | %s | %d~%d | %s%n",
                job.getAnnoId(), job.getAnnoSubject(), job.getClassCdNm(), job.getEmpTypeCdNm(),
                job.getPersonalHistory(), job.getPersonalHistoryEnd(), job.getEndDate()));
        }
        Path out = Path.of("build", "kakao-mobility-live-check.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println("결과 파일: " + out.toAbsolutePath());
    }
}
