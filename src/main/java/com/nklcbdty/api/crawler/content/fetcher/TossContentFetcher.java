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
 * 토스 본문.
 *
 * <p>토스 채용은 Greenhouse 를 쓰지만(링크가 {@code ?gh_jid=}) 공개 보드가 열려 있지 않아
 * ({@code boards-api.greenhouse.io/v1/boards/toss} 404) 토스가 감싼 API 를 쓴다.
 * 목록 API({@code /career/job-groups})에는 본문이 없고 상세에만 있다.</p>
 *
 * <p>본문이 {@code <p>#LI-DNI</p>} 처럼 껍데기만 오는 공고가 있다. 그런 건 수집 실패로 두고
 * {@link GenericHtmlContentFetcher} 가 아니라 다음 주기에 다시 시도하게 둔다 — 토스 상세페이지도
 * 클라이언트 렌더링이라 HTML 을 받아봐야 마찬가지다.</p>
 */
@Component
@Order(30)
@Slf4j
public class TossContentFetcher implements JobContentFetcher {

    private static final String DETAIL_API =
        "https://api-public.toss.im/api/v3/ipd-eggnog/career/jobs/%s";

    private final CrawlerCommonService commonService;

    public TossContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        return "TOSS".equalsIgnoreCase(job.getCompanyCd())
            && job.getAnnoId() != null && !job.getAnnoId().isBlank();
    }

    @Override
    public Fetched fetch(Job_mst job) {
        String raw = commonService.fetchApiResponse(String.format(DETAIL_API, job.getAnnoId().trim()));

        JSONObject success = new JSONObject(raw).optJSONObject("success");
        if (success == null) {
            log.warn("토스 본문: success 없음 annoId={}", job.getAnnoId());
            return null;
        }

        String html = success.optString("content", "");
        if (html.isBlank()) {
            return null;
        }
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    @Override
    public String sourceName() {
        return "toss-api";
    }
}
