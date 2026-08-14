package com.nklcbdty.api.crawler.content;

import com.nklcbdty.common.vo.Job_mst;

/**
 * 회사별 공고 본문 수집 전략.
 *
 * <p>크롤러(=목록 수집)와 분리한 이유: 본문은 대부분 상세 API/페이지를 한 번 더 받아야 나오고,
 * 목록 크롤은 이미 24분이 걸린다. 본문은 {@link JobContentIndexer} 가 뒤에서 천천히 채운다.
 * 크롤러 코드를 건드리지 않으므로 크롤러가 개편돼도 이쪽은 그대로다.</p>
 *
 * <p>여러 구현이 {@code supports} 를 만족하면 {@code @Order} 가 낮은 것이 이긴다.
 * 회사 전용 구현이 먼저 잡히고, 아무도 못 잡으면 {@code GenericHtmlContentFetcher} 가 받는다.</p>
 */
public interface JobContentFetcher {

    boolean supports(Job_mst job);

    /**
     * 본문을 가져온다.
     *
     * @return 본문. 못 가져오면 null 을 돌려 다음 시도에 맡긴다(예외를 던져도 된다).
     */
    Fetched fetch(Job_mst job) throws Exception;

    /** 수집 방식 식별자. {@code job_content.source} 에 남는다. */
    String sourceName();

    /**
     * 수집 결과. html 은 원본이 HTML 일 때만 채운다.
     *
     * @param html 원본 HTML (없으면 null)
     * @param text 태그를 걷어낸 본문 텍스트
     */
    record Fetched(String html, String text) {

        public static Fetched ofHtml(String html, String text) {
            return new Fetched(html, text);
        }

        public static Fetched ofText(String text) {
            return new Fetched(null, text);
        }

        public boolean isEmpty() {
            return text == null || text.isBlank();
        }
    }
}
