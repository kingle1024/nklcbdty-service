package com.nklcbdty.api.mycalendar.service;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.nklcbdty.api.crawler.common.CompanyEnums;

/**
 * 공고 주소에서 회사명을 추측한다. "나의 채용 캘린더" 에서 URL 만 붙여넣어도 회사명이 채워지도록.
 *
 * <p>어디까지나 입력 도우미다. 확신할 수 없으면 억지로 만들지 않고 {@code null} 을 돌려주고,
 * 회사명은 사용자가 직접 적는다(회사명이 이 화면의 유일한 필수값인 이유이기도 하다).</p>
 *
 * <p>추측 순서:</p>
 * <ol>
 *   <li>사람인·원티드 같은 <b>채용 사이트</b> 주소면 포기한다 — 도메인에서 뽑으면 "사람인" 이 나온다.</li>
 *   <li>그린하우스·레버 같은 <b>채용 솔루션(ATS)</b> 주소면 정해진 자리에서 회사 슬러그를 꺼낸다.</li>
 *   <li>그 외에는 호스트에서 careers/recruit 같은 접두 라벨을 걷어내고 남는 첫 라벨을 쓴다.</li>
 * </ol>
 *
 * <p>이렇게 얻은 슬러그가 우리가 아는 회사면({@link CompanyEnums}) 한글 회사명으로 바꾼다.
 * 모르는 회사면 슬러그를 사람이 읽을 만하게 다듬어서 준다(stripe → Stripe).</p>
 */
public final class CompanyNameGuesser {

    /**
     * 회사가 아니라 "채용 사이트"·문서 도구인 주소. 여기서는 도메인이 회사를 알려주지 않는다.
     * 호스트 전체와 등록 도메인 양쪽으로 비교하므로 둘 중 편한 쪽으로 적으면 된다
     * (open.kakao.com 은 막고 careers.kakao.com 은 살려야 해서 호스트 단위 항목이 필요하다).
     */
    private static final Set<String> JOB_BOARD_DOMAINS = Set.of(
        "saramin.co.kr", "jobkorea.co.kr", "wanted.co.kr", "jumpit.co.kr", "programmers.co.kr",
        "rocketpunch.com", "catch.co.kr", "incruit.com", "jobplanet.co.kr", "worknet.go.kr",
        "linkedin.com", "indeed.com", "glassdoor.com", "wellfound.com", "remoteok.com",
        "notion.site", "notion.so", "docs.google.com", "forms.gle", "bit.ly",
        "naver.me", "open.kakao.com"
    );

    /** 경로 첫 조각이 회사 슬러그인 ATS. 예: boards.greenhouse.io/<b>toss</b>/jobs/123 */
    private static final Set<String> PATH_SLUG_ATS_HOSTS = Set.of(
        "boards.greenhouse.io", "job-boards.greenhouse.io", "boards.eu.greenhouse.io",
        "jobs.lever.co", "jobs.eu.lever.co",
        "jobs.ashbyhq.com",
        "apply.workable.com",
        "careers.smartrecruiters.com",
        "jobs.jobvite.com"
    );

    /** 맨 앞 라벨이 회사 슬러그인 ATS. 예: <b>yanolja</b>.wd102.myworkdayjobs.com */
    private static final List<String> SUBDOMAIN_SLUG_ATS_SUFFIXES = List.of(
        "myworkdayjobs.com", "recruitee.com", "teamtailor.com", "workable.com",
        "bamboohr.com", "applytojob.com", "breezy.hr", "icims.com", "taleo.net",
        "personio.de", "personio.com", "factorialhr.com", "hibob.com"
    );

    /**
     * 회사명이 아니라 "채용" 을 뜻하는 접두 라벨. careers.kakao.com 에서 kakao 를 꺼내기 위해 걷어낸다.
     * (전부 걷어내서 남는 게 없으면 걷어내기 전 값을 쓴다 — jobs.jobs 같은 경우)
     */
    private static final Set<String> RECRUITING_LABELS = Set.of(
        "www", "m", "ko", "kr", "en", "us", "global",
        "career", "careers", "recruit", "recruiting", "recruitment",
        "job", "jobs", "apply", "hire", "hiring", "talent", "people",
        "join", "work", "employment", "boards", "board"
    );

    /**
     * 슬러그 → 한글 회사명. {@link CompanyEnums} 의 채용 페이지 주소를 같은 규칙으로 돌려서 만든다.
     * enum 에 회사가 추가되면 여기도 저절로 따라온다.
     */
    private static final Map<String, String> KNOWN_COMPANY_NAMES = buildKnownCompanyNames();

    /**
     * 같은 회사의 다른 도메인. 위 자동 생성 표는 채용 페이지 주소 하나만 알기 때문에
     * (예: 네이버는 recruit.<b>navercorp</b>.com) 흔히 쓰는 다른 표기를 손으로 채워 둔다.
     */
    private static final Map<String, String> COMPANY_ALIASES = Map.ofEntries(
        Map.entry("naver", "네이버"),
        Map.entry("navercorp", "네이버"),
        Map.entry("line", "라인"),
        Map.entry("linecorp", "라인"),
        Map.entry("linepluscorp", "라인"),
        Map.entry("woowahan", "배달의민족"),
        Map.entry("woowa", "배달의민족"),
        Map.entry("woowabros", "배달의민족"),
        Map.entry("baemin", "배달의민족"),
        Map.entry("coupang", "쿠팡"),
        Map.entry("daangn", "당근마켓"),
        Map.entry("karrot", "당근마켓"),
        Map.entry("toss", "토스"),
        Map.entry("tossbank", "토스뱅크"),
        Map.entry("yanolja", "야놀자"),
        Map.entry("kakaobank", "카카오뱅크"),
        Map.entry("kakaopay", "카카오페이"),
        Map.entry("kakaoenterprise", "카카오엔터프라이즈"),
        Map.entry("kakaostyle", "카카오스타일"),
        Map.entry("nexon", "넥슨"),
        Map.entry("ncsoft", "엔씨소프트"),
        Map.entry("krafton", "크래프톤"),
        Map.entry("kakaogames", "카카오게임즈")
    );

    private CompanyNameGuesser() {
    }

    /**
     * @param url 사용자가 붙여넣은 주소. 스킴이 없어도(careers.kakao.com/jobs) 된다.
     * @return 추측한 회사명. 추측할 수 없으면 null.
     */
    public static String guess(String url) {
        final String slug = slugOf(url);
        if (slug == null) {
            return null;
        }
        final String known = KNOWN_COMPANY_NAMES.get(slug);
        if (known != null) {
            return known;
        }
        final String alias = COMPANY_ALIASES.get(slug);
        return alias != null ? alias : prettify(slug);
    }

    /** 주소에서 회사 슬러그(영문 소문자)를 꺼낸다. 못 꺼내면 null. */
    private static String slugOf(String url) {
        final URI uri = parse(url);
        if (uri == null) {
            return null;
        }
        final String host = hostOf(uri);
        if (host == null
            || JOB_BOARD_DOMAINS.contains(host)
            || JOB_BOARD_DOMAINS.contains(registrableDomain(host))) {
            return null;
        }

        if (PATH_SLUG_ATS_HOSTS.contains(host)) {
            return normalize(firstPathSegment(uri));
        }

        final String[] labels = host.split("\\.");
        for (String suffix : SUBDOMAIN_SLUG_ATS_SUFFIXES) {
            // 맨 앞 라벨이 곧 회사 — 단 apply.workable.com 처럼 접두 라벨이 앞에 오면 회사가 아니다.
            if (host.endsWith("." + suffix) && !RECRUITING_LABELS.contains(labels[0])) {
                return normalize(labels[0]);
            }
        }

        return normalize(firstMeaningfulLabel(labels));
    }

    /**
     * careers/recruit 같은 접두 라벨을 걷어내고 첫 라벨을 고른다.
     * 전부 걷어내지고 남는 게 없으면(예: jobs.co.kr) 걷어내기 전 첫 라벨로 되돌린다.
     */
    private static String firstMeaningfulLabel(String[] labels) {
        for (String label : labels) {
            if (!RECRUITING_LABELS.contains(label)) {
                return label;
            }
        }
        return labels[0];
    }

    /** 스킴이 없으면 붙여서 파싱한다. 사용자는 careers.kakao.com 처럼 붙여넣는 일이 잦다. */
    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        final String trimmed = url.trim();
        final String withScheme = trimmed.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? trimmed : "https://" + trimmed;
        try {
            return URI.create(withScheme);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String hostOf(URI uri) {
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        final String lower = host.toLowerCase(Locale.ROOT);
        // IP 주소는 회사를 알려주지 않는다.
        return lower.matches("[0-9.]+") ? null : lower;
    }

    /**
     * 채용 사이트 판별용 등록 도메인. co.kr·go.kr 처럼 2단계 국가 도메인은 라벨 3개까지 본다.
     * (완전한 public suffix 목록이 필요한 일이 아니라 이 정도로 충분하다)
     */
    private static String registrableDomain(String host) {
        final String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return host;
        }
        final boolean twoLevelTld = labels.length >= 3
            && labels[labels.length - 1].length() == 2
            && labels[labels.length - 2].length() <= 3;
        final int take = twoLevelTld ? 3 : 2;
        return String.join(".", Arrays.copyOfRange(labels, labels.length - take, labels.length));
    }

    private static String firstPathSegment(URI uri) {
        final String path = uri.getPath();
        if (path == null) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                return segment;
            }
        }
        return null;
    }

    /** 슬러그로 쓸 수 있는 값만 통과시킨다(영문/숫자/하이픈). 순수 숫자나 빈 값은 회사가 아니다. */
    private static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        final String lower = candidate.toLowerCase(Locale.ROOT).trim();
        if (!lower.matches("[a-z0-9-]{2,}") || lower.matches("[0-9-]+")) {
            return null;
        }
        return lower;
    }

    /** 모르는 회사의 슬러그를 사람이 읽을 만하게. my-company → My Company */
    private static String prettify(String slug) {
        final StringBuilder result = new StringBuilder();
        for (String word : slug.split("-")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static Map<String, String> buildKnownCompanyNames() {
        final Map<String, String> names = new LinkedHashMap<>();
        for (CompanyEnums company : CompanyEnums.values()) {
            final String slug = slugOf(company.getCareerPageUrl());
            if (slug != null) {
                names.putIfAbsent(slug, company.getCompanyNm());
            }
        }
        return new HashMap<>(names);
    }
}
