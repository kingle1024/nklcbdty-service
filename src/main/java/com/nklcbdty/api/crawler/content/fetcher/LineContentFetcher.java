package com.nklcbdty.api.crawler.content.fetcher;

import org.json.JSONObject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.content.ContentText;
import com.nklcbdty.api.crawler.content.JobContentFetcher;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 라인 본문.
 *
 * <p>채용 사이트가 Gatsby 라 페이지마다 {@code page-data.json} 이 따로 있다.
 * 목록 크롤러가 쓰는 {@code /page-data/ko/jobs/page-data.json} 과 같은 계열이고,
 * 공고별 경로에 {@code strapiId}(=annoId)를 넣으면 본문({@code strapiJobs.content})이 나온다.</p>
 */
@Component
@Order(40)
@Slf4j
public class LineContentFetcher implements JobContentFetcher {

    private static final String PAGE_DATA = "https://careers.linecorp.com/page-data/ko/jobs/%s/page-data.json";

    private final CrawlerCommonService commonService;

    public LineContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        return "LINE".equalsIgnoreCase(job.getCompanyCd())
            && job.getAnnoId() != null && !job.getAnnoId().isBlank();
    }

    @Override
    public Fetched fetch(Job_mst job) {
        String raw = commonService.fetchApiResponse(String.format(PAGE_DATA, job.getAnnoId().trim()));

        JSONObject strapiJobs = new JSONObject(raw)
            .optJSONObject("result");
        strapiJobs = strapiJobs == null ? null : strapiJobs.optJSONObject("data");
        strapiJobs = strapiJobs == null ? null : strapiJobs.optJSONObject("strapiJobs");

        if (strapiJobs == null) {
            log.warn("라인 본문: strapiJobs 없음 annoId={}", job.getAnnoId());
            return null;
        }

        String html = strapiJobs.optString("content", "");
        if (html.isBlank()) {
            return null;
        }
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    @Override
    public String sourceName() {
        return "line-page-data";
    }
}
