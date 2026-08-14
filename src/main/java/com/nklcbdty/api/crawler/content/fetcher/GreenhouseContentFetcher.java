package com.nklcbdty.api.crawler.content.fetcher;

import java.util.Map;

import org.json.JSONObject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.content.ContentText;
import com.nklcbdty.api.crawler.content.JobContentFetcher;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * Greenhouse 공개 보드를 쓰는 회사(당근·쿠팡)의 본문.
 *
 * <p>{@code anno_id} 가 곧 Greenhouse job id(=gh_jid)라 그대로 상세를 부를 수 있다.
 * 본문은 escape 된 HTML 로 오므로 {@link ContentText} 가 풀어서 텍스트로 바꾼다.</p>
 */
@Component
@Order(20)
@Slf4j
public class GreenhouseContentFetcher implements JobContentFetcher {

    private static final String DETAIL_API = "https://boards-api.greenhouse.io/v1/boards/%s/jobs/%s";

    /** company_cd → Greenhouse 보드 이름. */
    private static final Map<String, String> BOARDS = Map.of(
        "DAANGN", "daangn",
        "COUPANG", "coupang"
    );

    private final CrawlerCommonService commonService;

    public GreenhouseContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        return boardOf(job) != null && job.getAnnoId() != null && !job.getAnnoId().isBlank();
    }

    @Override
    public Fetched fetch(Job_mst job) {
        String board = boardOf(job);
        String raw = commonService.fetchApiResponse(
            String.format(DETAIL_API, board, job.getAnnoId().trim()));

        String html = new JSONObject(raw).optString("content", "");
        if (html.isBlank()) {
            log.warn("Greenhouse 본문 비어있음 board={} annoId={}", board, job.getAnnoId());
            return null;
        }
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    private String boardOf(Job_mst job) {
        String companyCd = job.getCompanyCd();
        return companyCd == null ? null : BOARDS.get(companyCd.toUpperCase());
    }

    @Override
    public String sourceName() {
        return "greenhouse-api";
    }
}
