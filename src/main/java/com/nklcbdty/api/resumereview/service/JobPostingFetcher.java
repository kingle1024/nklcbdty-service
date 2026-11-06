package com.nklcbdty.api.resumereview.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 채용 공고 주소를 받아 공고 본문을 읽어 온다.
 *
 * <p>사용자가 준 주소를 서버가 대신 여는 것이라 내부망을 찌르는 통로(SSRF)가 되지 않게 막는다.
 * 호스트를 미리 풀어서 사설·루프백·링크로컬 주소면 거절하고, 리다이렉트도 자동으로 따라가지 않고
 * 한 번씩 같은 검사를 거친다.</p>
 *
 * <p>JS 로만 그리는 페이지는 본문이 비어 있을 수 있다. 그때는 공고 내용을 직접 붙여넣어 달라고 안내한다.</p>
 */
@Slf4j
@Component
public class JobPostingFetcher {

    static final int MAX_CHARS = 12_000;
    /** 이보다 짧으면 공고 본문을 못 읽은 것으로 본다 */
    static final int MIN_USEFUL_CHARS = 200;
    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    /** 테스트에서 DNS 를 바꿔 끼우려고 둔다 */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;

    public JobPostingFetcher() {
        this(InetAddress::getAllByName);
    }

    JobPostingFetcher(HostResolver resolver) {
        this.resolver = resolver;
    }

    public String fetch(String rawUrl) {
        String url = checkPublicUrl(rawUrl);
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                Connection.Response res = Jsoup.connect(url)
                                               .userAgent(USER_AGENT)
                                               .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                                               .followRedirects(false)
                                               .ignoreHttpErrors(true)
                                               .timeout(TIMEOUT_MS)
                                               .maxBodySize(MAX_BODY_BYTES)
                                               .execute();
                int status = res.statusCode();
                if (status >= 300 && status < 400 && res.hasHeader("Location")) {
                    url = checkPublicUrl(URI.create(url).resolve(res.header("Location")).toString());
                    continue;
                }
                if (status >= 400) {
                    throw ResumeReviewException.badInput(
                            "공고 페이지를 열지 못했습니다. (HTTP " + status + ") 공고 내용을 직접 붙여넣어 주세요.");
                }
                String text = extractText(res.parse());
                if (text.length() < MIN_USEFUL_CHARS) {
                    throw ResumeReviewException.badInput(
                            "공고 페이지에서 본문을 거의 읽지 못했습니다. 공고 내용을 직접 붙여넣어 주세요.");
                }
                return text;
            }
        } catch (IOException | IllegalArgumentException e) {
            log.info("공고 페이지 읽기 실패 url={} reason={}", url, e.getMessage());
            throw ResumeReviewException.badInput("공고 페이지를 읽지 못했습니다. 공고 내용을 직접 붙여넣어 주세요.");
        }
        throw ResumeReviewException.badInput("공고 주소가 너무 여러 번 다른 곳으로 넘어갑니다. 공고 내용을 직접 붙여넣어 주세요.");
    }

    /**
     * http(s) 이고, 호스트가 가리키는 주소가 모두 공인 주소일 때만 통과시킨다.
     *
     * @return 앞뒤 공백을 뗀 주소
     */
    String checkPublicUrl(String rawUrl) {
        final String notAllowed = "공개된 채용 페이지 주소만 읽을 수 있습니다.";
        if (rawUrl == null || rawUrl.isBlank()) {
            throw ResumeReviewException.badInput("공고 주소가 비어 있습니다.");
        }
        final String url = rawUrl.strip();
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw ResumeReviewException.badInput("공고 주소 형식이 올바르지 않습니다.");
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ResumeReviewException.badInput("공고 주소는 http 또는 https 로 시작해야 합니다.");
        }
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw ResumeReviewException.badInput("공고 주소 형식이 올바르지 않습니다.");
        }
        final InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw ResumeReviewException.badInput("공고 주소의 사이트를 찾을 수 없습니다.");
        }
        if (addresses.length == 0) {
            throw ResumeReviewException.badInput("공고 주소의 사이트를 찾을 수 없습니다.");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw ResumeReviewException.badInput(notAllowed);
            }
        }
        return url;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        final byte[] b = address.getAddress();
        if (b.length == 4) {
            final int first = b[0] & 0xff;
            final int second = b[1] & 0xff;
            // 0.0.0.0/8, 100.64.0.0/10(CGNAT), 198.18.0.0/15(벤치마크), 240.0.0.0/4(예약)
            return first != 0
                   && !(first == 100 && second >= 64 && second < 128)
                   && !(first == 198 && (second == 18 || second == 19))
                   && first < 240;
        }
        // IPv6 고유 로컬 fc00::/7
        return (b[0] & 0xfe) != 0xfc;
    }

    /**
     * 페이지에서 공고 글을 뽑는다. 구조화 데이터(JSON-LD JobPosting)가 있으면 그것을 먼저 쓰고,
     * 본문 글자도 덧붙인다 — 구조화 데이터엔 요약만 있는 사이트가 많다.
     */
    static String extractText(Document doc) {
        final List<String> parts = new ArrayList<>();

        final String title = firstNonBlank(meta(doc, "og:title"), doc.title());
        if (!title.isBlank()) {
            parts.add("제목: " + title);
        }
        final String description = firstNonBlank(meta(doc, "og:description"), meta(doc, "description"));
        if (!description.isBlank()) {
            parts.add(description);
        }
        for (Element script : doc.select("script[type=application/ld+json]")) {
            collectJobPostings(script.data(), parts);
        }

        final Element body = doc.body();
        if (body != null) {
            final Element copy = body.clone();
            copy.select("script, style, noscript, svg, header, footer, nav, iframe, form, button").remove();
            copy.select("br").before("\n");
            copy.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, section, article, dt, dd").before("\n");
            parts.add(copy.wholeText());
        }

        final String text = ReviewDocumentExtractor.normalize(String.join("\n", parts));
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private static void collectJobPostings(String json, List<String> out) {
        try {
            final Object parsed = json.strip().startsWith("[") ? new JSONArray(json) : new JSONObject(json);
            collectJobPostings(parsed, out);
        } catch (RuntimeException e) {
            // 깨진 JSON-LD 는 흔하다. 본문 글자로 충분하니 넘어간다.
        }
    }

    private static void collectJobPostings(Object node, List<String> out) {
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                collectJobPostings(array.opt(i), out);
            }
            return;
        }
        if (!(node instanceof JSONObject object)) {
            return;
        }
        if (object.has("@graph")) {
            collectJobPostings(object.opt("@graph"), out);
        }
        if (!"JobPosting".equals(object.optString("@type"))) {
            return;
        }
        final JSONObject org = object.optJSONObject("hiringOrganization");
        if (org != null && !org.optString("name").isBlank()) {
            out.add("회사: " + org.optString("name"));
        }
        if (!object.optString("title").isBlank()) {
            out.add("직무: " + object.optString("title"));
        }
        final String html = object.optString("description");
        if (!html.isBlank()) {
            final Document fragment = Jsoup.parseBodyFragment(html);
            fragment.select("br").before("\n");
            fragment.select("p, div, li, h1, h2, h3, h4, tr").before("\n");
            out.add(fragment.body().wholeText());
        }
    }

    private static String meta(Document doc, String name) {
        final Element el = doc.selectFirst("meta[property=" + name + "], meta[name=" + name + "]");
        return el == null ? "" : el.attr("content").strip();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return "";
    }
}
