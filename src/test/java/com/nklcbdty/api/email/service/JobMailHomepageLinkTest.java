package com.nklcbdty.api.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.common.dto.JobPosting;
import com.nklcbdty.common.email.JobEmailContentBuilder;

// 메일 본문의 "더 많은 채용 공고를 보시려면 ..." 안내 링크는 nklcbdty-common 의
// JobEmailContentBuilder 가 만든다. 예전에 이 링크가 DNS 조차 잡혀 있지 않은
// nklcb.co.kr 을 가리켰고, common 에서 고친 뒤에도 이 저장소는 옛 버전을 물고
// 있어서 실제 발송된 메일은 한동안 죽은 링크 그대로였다.
// 검증 대상이 "빌드가 실제로 해석한 common jar 의 출력" 이므로, 링크가 되돌아가도
// 의존 버전이 낮은 쪽으로 내려가도 여기서 잡힌다.
class JobMailHomepageLinkTest {

    private static final String HOMEPAGE_URL = "https://nklcb.netlify.app";

    @Test
    @DisplayName("메일 본문은 운영 홈페이지(nklcb.netlify.app)로 링크한다")
    void html_linksToLiveHomepage() {
        String html = JobEmailContentBuilder.generateHtml("backend", List.of(posting()));

        assertThat(html).contains("href=\"" + HOMEPAGE_URL + "\"");
    }

    @Test
    @DisplayName("DNS 가 잡히지 않는 nklcb.co.kr 은 메일 본문에 남아있지 않다")
    void html_hasNoDeadDomain() {
        String html = JobEmailContentBuilder.generateHtml("backend", List.of(posting()));

        assertThat(html).doesNotContain("nklcb.co.kr");
    }

    private JobPosting posting() {
        JobPosting posting = new JobPosting();
        posting.setTitle("백엔드 개발자");
        posting.setCompany("naver");
        posting.setUrl("https://recruit.navercorp.com/1");
        posting.setJobType("Backend");
        posting.setEndDate("2999-12-31 23:59:59");
        return posting;
    }
}
