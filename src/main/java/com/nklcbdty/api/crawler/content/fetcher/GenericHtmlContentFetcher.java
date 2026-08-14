package com.nklcbdty.api.crawler.content.fetcher;

import java.util.List;

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
 * 전용 수집기가 없는 회사의 본문. 상세페이지 HTML 에서 본문으로 보이는 덩어리를 골라낸다.
 *
 * <p>주 대상은 카카오(계열사마다 채용 시스템이 달라 호스트가 7개다 — recruiter.co.kr, ninehire,
 * careers.kakao.com …)와 야놀자다. 계열사별 전용 수집기를 7개 만드는 대신, 흔한 본문 컨테이너를
 * 순서대로 시도하고 없으면 body 에서 껍데기를 걷어낸다.</p>
 *
 * <p>서버 렌더링이 아니면(=SPA) 본문이 안 나온다. 그 경우 {@link com.nklcbdty.api.crawler.content.JobContentService}
 * 가 "의미 있는 길이" 미달로 실패 처리하고, 나중에 그 회사 전용 수집기를 붙이면 된다.</p>
 */
@Component
@Order(Integer.MAX_VALUE) // 아무도 안 잡을 때만
@Slf4j
public class GenericHtmlContentFetcher implements JobContentFetcher {

    /** 앞에 있을수록 우선. 채용 사이트에서 흔한 본문 컨테이너들. */
    private static final List<String> CONTENT_SELECTORS = List.of(
        "[class*=jobDescription]", "[class*=job-description]", "[class*=recruit-detail]",
        "[class*=jobDetail]", "[class*=job_detail]", "[class*=detail_content]",
        "article", "main", "[role=main]", "#content", ".content"
    );

    /** 어느 컨테이너를 잡든 항상 걷어낼 것들. */
    private static final String CHROME_SELECTOR =
        "script, style, noscript, nav, header, footer, aside, form, iframe";

    private final CrawlerCommonService commonService;

    public GenericHtmlContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        String link = job.getJobDetailLink();
        return link != null && link.startsWith("http");
    }

    @Override
    public Fetched fetch(Job_mst job) throws Exception {
        Document doc = commonService.jsoupConnect(job.getJobDetailLink()).get();
        doc.select(CHROME_SELECTOR).remove();

        Element best = null;
        for (String selector : CONTENT_SELECTORS) {
            Element candidate = longestMatch(doc, selector);
            if (candidate != null && ContentText.isMeaningful(candidate.text())) {
                best = candidate;
                break;
            }
        }
        if (best == null) {
            best = doc.body();
        }
        if (best == null) {
            return null;
        }

        String html = best.html();
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    /** 같은 선택자에 여러 개가 걸리면 텍스트가 가장 긴 것이 본문일 가능성이 높다. */
    private Element longestMatch(Document doc, String selector) {
        Element best = null;
        int bestLength = 0;
        for (Element element : doc.select(selector)) {
            int length = element.text().length();
            if (length > bestLength) {
                best = element;
                bestLength = length;
            }
        }
        return best;
    }

    @Override
    public String sourceName() {
        return "generic-html";
    }
}
