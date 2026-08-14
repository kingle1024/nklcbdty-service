package com.nklcbdty.api.crawler.content.fetcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.content.ContentText;
import com.nklcbdty.api.crawler.content.JobContentFetcher;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 본문.
 *
 * <p>{@code recruit.navercorp.com/rcrt/view.do?annoId=...} 는 서버 렌더링이라 HTML 에 본문이 그대로 있다.
 * 다만 페이지 전체를 텍스트로 만들면 GNB·푸터가 절반이라, 본문 컨테이너({@code .detail_wrap})만 잘라낸다.</p>
 */
@Component
@Order(50)
@Slf4j
public class NaverContentFetcher implements JobContentFetcher {

    private static final String CONTENT_SELECTOR = ".detail_wrap";

    private final CrawlerCommonService commonService;

    public NaverContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        String link = job.getJobDetailLink();
        return "NAVER".equalsIgnoreCase(job.getCompanyCd())
            && link != null && link.contains("recruit.navercorp.com");
    }

    @Override
    public Fetched fetch(Job_mst job) throws Exception {
        Document doc = commonService.jsoupConnect(job.getJobDetailLink()).get();

        Element body = doc.selectFirst(CONTENT_SELECTOR);
        if (body == null) {
            // 마크업이 바뀐 것이므로 조용히 넘기지 않는다. 전 공고가 한꺼번에 빈 본문이 된다.
            log.warn("네이버 본문: {} 를 못 찾음 link={}", CONTENT_SELECTOR, job.getJobDetailLink());
            return null;
        }

        String html = body.html();
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    @Override
    public String sourceName() {
        return "naver-html";
    }
}
