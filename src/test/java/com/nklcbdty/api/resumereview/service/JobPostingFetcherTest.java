package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class JobPostingFetcherTest {

    private static JobPostingFetcher resolvingTo(String ip) {
        return new JobPostingFetcher(host -> new InetAddress[] {InetAddress.getByName(ip)});
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.0.10", "169.254.169.254",
                            "0.0.0.0", "100.64.1.1", "::1", "fd00::1", "fe80::1"})
    @DisplayName("내부망·루프백·메타데이터 주소로 풀리는 호스트는 거절한다")
    void rejectsPrivateAddresses(String ip) {
        assertThatThrownBy(() -> resolvingTo(ip).checkPublicUrl("https://evil.example.com/job"))
                .isInstanceOf(ResumeReviewException.class)
                .hasMessageContaining("공개된 채용 페이지");
    }

    @Test
    @DisplayName("공인 주소로 풀리는 http(s) 주소는 통과한다")
    void allowsPublic() {
        assertThat(resolvingTo("13.124.10.10").checkPublicUrl(" https://www.wanted.co.kr/wd/1 "))
                .isEqualTo("https://www.wanted.co.kr/wd/1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/a", "javascript:alert(1)", "not a url"})
    @DisplayName("http(s) 가 아닌 주소는 거절한다")
    void rejectsOtherSchemes(String url) {
        assertThatThrownBy(() -> resolvingTo("13.124.10.10").checkPublicUrl(url))
                .isInstanceOf(ResumeReviewException.class);
    }

    @Test
    @DisplayName("없는 호스트는 찾을 수 없다고 알려준다")
    void unknownHost() {
        JobPostingFetcher fetcher = new JobPostingFetcher(host -> {
            throw new UnknownHostException(host);
        });
        assertThatThrownBy(() -> fetcher.checkPublicUrl("https://no-such-host.example/job"))
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("JSON-LD JobPosting 과 본문을 함께 뽑고, 스크립트·내비게이션은 버린다")
    void extractsJobPosting() {
        String html = """
                <html><head><title>백엔드 개발자 | 원티드</title>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting","title":"백엔드 개발자",
                 "hiringOrganization":{"@type":"Organization","name":"에스케이"},
                 "description":"<p>주요업무</p><ul><li>Spring 기반 API 개발</li><li>MSA 전환</li></ul>"}
                </script></head>
                <body><nav>메뉴 로그인</nav><main><h2>자격요건</h2><p>Java 3년 이상</p></main>
                <script>var secret = 1;</script></body></html>""";

        String text = JobPostingFetcher.extractText(Jsoup.parse(html));

        assertThat(text).contains("제목: 백엔드 개발자 | 원티드", "회사: 에스케이", "직무: 백엔드 개발자",
                                  "Spring 기반 API 개발\nMSA 전환", "자격요건\nJava 3년 이상");
        assertThat(text).doesNotContain("secret", "메뉴 로그인");
    }
}
