package com.nklcbdty.api.crawler.content.fetcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.content.ContentText;
import com.nklcbdty.api.crawler.content.JobContentFetcher;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 배민(우아한형제들) 본문.
 *
 * <p>상세페이지는 Next.js 클라이언트 렌더링이라 HTML 을 받아도 본문이 없다. 채용 API 를 쓴다.
 * 주의: 상세 API 의 키는 목록의 {@code recruitSeq}(=annoId)가 아니라 {@code recruitNumber}(R26xxxxx)다.
 * {@code /w1/recruits/25163} 은 {@code code=9002, data=null} 로 돌아온다.</p>
 */
@Component
@Order(10)
@Slf4j
public class BaeminContentFetcher implements JobContentFetcher {

    private static final String DETAIL_API = "https://career.woowahan.com/w1/recruits/%s";
    private static final Pattern RECRUIT_NUMBER = Pattern.compile("/recruitment/([^/?#]+)/detail");

    private final CrawlerCommonService commonService;

    public BaeminContentFetcher(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public boolean supports(Job_mst job) {
        String link = job.getJobDetailLink();
        return "BAEMIN".equalsIgnoreCase(job.getCompanyCd())
            || (link != null && link.contains("career.woowahan.com"));
    }

    @Override
    public Fetched fetch(Job_mst job) {
        String recruitNumber = recruitNumberOf(job);
        if (recruitNumber == null) {
            log.warn("배민 본문: 링크에서 recruitNumber 를 못 찾음 link={}", job.getJobDetailLink());
            return null;
        }

        String raw = commonService.fetchApiResponse(String.format(DETAIL_API, recruitNumber));
        JSONObject data = new JSONObject(raw).optJSONObject("data");
        if (data == null) {
            log.warn("배민 본문: data 없음 recruitNumber={} 응답앞부분={}",
                recruitNumber, raw == null ? "null" : raw.substring(0, Math.min(150, raw.length())));
            return null;
        }

        String html = data.optString("recruitContents", "");
        if (html.isBlank()) {
            return null;
        }
        return Fetched.ofHtml(html, ContentText.htmlToText(html));
    }

    private String recruitNumberOf(Job_mst job) {
        String link = job.getJobDetailLink();
        if (link == null) {
            return null;
        }
        Matcher m = RECRUIT_NUMBER.matcher(link);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public String sourceName() {
        return "baemin-api";
    }
}
