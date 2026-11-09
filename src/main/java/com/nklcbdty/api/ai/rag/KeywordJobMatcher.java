package com.nklcbdty.api.ai.rag;

import com.nklcbdty.api.crawler.common.JobEndDates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 임베딩 없이 키워드로 공고를 고르는 대체 매칭.
 *
 * <p>의미 검색은 OpenAI 임베딩에 기대는데, 쿼터가 바닥나면(HTTP 429) 인덱싱이 0건이 되어
 * 이력서 매칭·자연어 검색이 통째로 멈춘다(2026-10-06, 2026-11-09 두 번). 그동안에도 사용자가
 * 결과를 받도록, 공고 제목·직무·회사·근무지에서 뽑은 낱말이 이력서에 얼마나 들어 있는지로 순위를 매긴다.</p>
 *
 * <p>점수는 IDF 가중 코사인(0~1)이다. 흔한 낱말(예: "개발")보다 드문 낱말(예: "Kafka")이 더 무겁다.
 * 공고 문서는 DB 에서 가볍게(필요한 컬럼만) 읽어 10분 동안 메모리에 둔다.</p>
 */
@Slf4j
@Service
public class KeywordJobMatcher {

    private static final Duration REFRESH = Duration.ofMinutes(10);

    /** 한글 덩어리, 또는 영문·숫자(+ c++, c#, node.js 같은 기호 포함) 덩어리. */
    private static final Pattern TOKEN = Pattern.compile("[가-힣]+|[a-z0-9][a-z0-9+#.]*");

    /** 공고 제목에 거의 다 붙어서 순위를 흐리는 말. */
    private static final Set<String> STOPWORDS = Set.of(
            "채용", "모집", "경력", "신입", "경력직", "신입사원", "정규직", "계약직", "인턴", "인턴십", "공고",
            "담당", "담당자", "분야", "부문", "직무", "및", "또는", "수시", "상시", "전환형", "체험형",
            "개발", "개발자", "엔지니어", "엔지니어링", "소프트웨어", "서비스", "시스템", "테크", "기술", "기타",
            "developer", "developers", "engineer", "engineering", "software", "tech", "dev",
            "the", "and", "of", "for", "in", "to", "with", "a", "an", "at", "on", "or",
            "year", "years", "yrs", "new", "senior", "junior", "lead");

    /**
     * 같은 직군을 한글·영문으로 따로 적은 것을 한 낱말로 모은다(공고는 "프론트엔드", 이력서는 "Frontend" 처럼).
     * 키는 소문자. 한글 키는 이력서 본문에 조사가 붙어 있어도 잡히도록 부분 문자열로도 찾는다.
     */
    private static final Map<String, String> CANON = Map.ofEntries(
            Map.entry("프론트엔드", "frontend"), Map.entry("프론트", "frontend"),  
            Map.entry("front-end", "frontend"), Map.entry("웹프론트엔드", "frontend"),
            Map.entry("백엔드", "backend"), Map.entry("back-end", "backend"),
            Map.entry("서버", "backend"), Map.entry("server", "backend"), Map.entry("server-side", "backend"),
            Map.entry("데이터", "data"), Map.entry("dataengineering", "data"), Map.entry("안드로이드", "android"), Map.entry("아이오에스", "ios"),
            Map.entry("머신러닝", "ml"), Map.entry("machine", "ml"), Map.entry("인공지능", "ai"), Map.entry("딥러닝", "ml"),
            Map.entry("데브옵스", "devops"), Map.entry("인프라", "infra"), Map.entry("infrastructure", "infra"),
            Map.entry("보안", "security"), Map.entry("디자이너", "design"), Map.entry("디자인", "design"),
            Map.entry("designer", "design"), Map.entry("기획", "pm"), Map.entry("기획자", "pm"),
            Map.entry("마케팅", "marketing"), Map.entry("마케터", "marketing"), Map.entry("모바일", "mobile"),
            Map.entry("자바", "java"), Map.entry("스프링", "spring"), Map.entry("리액트", "react"),
            Map.entry("파이썬", "python"), Map.entry("코틀린", "kotlin"), Map.entry("스위프트", "swift"));

    /**
     * 공고 제목에는 기술 스택이 거의 없다("서버 개발자"). 이력서의 스택에서 직군을 짐작해 질의에 보탠다.
     * 질의 쪽에만 더한다 — 공고 쪽에 더하면 모든 백엔드 공고가 java 를 가진 것처럼 된다.
     */
    private static final Map<String, List<String>> HINTS = Map.ofEntries(
            Map.entry("java", List.of("backend")), Map.entry("spring", List.of("backend")),
            Map.entry("jpa", List.of("backend")), Map.entry("kafka", List.of("backend")),
            Map.entry("mysql", List.of("backend")), Map.entry("redis", List.of("backend")),
            Map.entry("django", List.of("backend")), Map.entry("node.js", List.of("backend")),
            Map.entry("nestjs", List.of("backend")), Map.entry("msa", List.of("backend")),
            Map.entry("microservices", List.of("backend")), Map.entry("golang", List.of("backend")),
            Map.entry("react", List.of("frontend")), Map.entry("vue", List.of("frontend")),
            Map.entry("vue.js", List.of("frontend")), Map.entry("typescript", List.of("frontend")),
            Map.entry("javascript", List.of("frontend")), Map.entry("next.js", List.of("frontend")),
            Map.entry("css", List.of("frontend")), Map.entry("html", List.of("frontend")),
            Map.entry("swift", List.of("ios", "mobile")), Map.entry("swiftui", List.of("ios", "mobile")),
            Map.entry("ios", List.of("mobile")), Map.entry("android", List.of("mobile")),
            Map.entry("kotlin", List.of("android")), Map.entry("flutter", List.of("mobile")),
            Map.entry("spark", List.of("data")), Map.entry("airflow", List.of("data")),
            Map.entry("hadoop", List.of("data")), Map.entry("etl", List.of("data")),
            Map.entry("pytorch", List.of("ml", "ai")), Map.entry("tensorflow", List.of("ml", "ai")),
            Map.entry("llm", List.of("ai", "ml")), Map.entry("kubernetes", List.of("devops", "infra")),
            Map.entry("k8s", List.of("devops", "infra")), Map.entry("terraform", List.of("devops", "infra")),
            Map.entry("figma", List.of("design")));

    /** 직군으로 보는 낱말(CANON 으로 모은 뒤의 이름). */
    private static final Set<String> FAMILIES = Set.of(
            "backend", "frontend", "data", "ios", "android", "mobile", "ml", "ai", "devops", "infra",
            "security", "design", "pm", "marketing", "dba", "qa");

    public record JobDoc(Long id, Set<String> tokens) {}

    public record Hit(Long jobId, float score) {}

    private final JobEmbeddingRepository repo;

    private volatile List<JobDoc> docs = List.of();
    private volatile Map<String, Double> idf = Map.of();
    private volatile LocalDateTime loadedAt;

    public KeywordJobMatcher(JobEmbeddingRepository repo) {
        this.repo = repo;
    }

    /** 이력서(또는 검색어)와 가장 많이 겹치는 공고 top-K. 겹치는 낱말이 하나도 없으면 빼고 준다. */
    public List<Hit> search(String text, int topK) {
        if (text == null || text.isBlank()) return List.of();
        ensureLoaded();
        return rank(docs, idf, text, topK);
    }

    static List<Hit> rank(List<JobDoc> docs, Map<String, Double> idf, String text, int topK) {
        final String haystack = text.toLowerCase(Locale.ROOT);
        final Set<String> queryTokens = queryTokens(haystack);
        if (queryTokens.isEmpty() || docs.isEmpty()) return List.of();

        // 이력서가 어느 직군인지 먼저 정하고 그 직군 공고 안에서만 고른다. 안 그러면 이력서에 우연히 있는
        // 드문 낱말(예: "정산")이 직군이 다른 공고를 위로 끌어올린다. 직군을 못 정했거나 그 직군 공고가
        // 하나도 없으면 전체에서 고른다.
        final Set<String> families = familiesOf(haystack);
        List<JobDoc> pool = docs;
        if (!families.isEmpty()) {
            final List<JobDoc> same = docs.stream()
                    .filter(d -> d.tokens().stream().anyMatch(families::contains))
                    .toList();
            if (!same.isEmpty()) pool = same;
        }

        final List<double[]> scored = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        for (JobDoc d : pool) {
            double dot = 0;
            double docNorm = 0;
            for (String t : d.tokens()) {
                final double w = idf.getOrDefault(t, 0.0);
                docNorm += w * w;
                if (matches(t, queryTokens, haystack)) dot += w * w;
            }
            if (dot <= 0 || docNorm <= 0) continue;
            // 점수 = 공고 키워드(IDF 가중) 중 이력서에 들어 있는 몫. 이력서는 길고 공고 제목은 짧아서
            // 양쪽 코사인으로 재면 다 맞아도 20% 남짓으로 나온다 — "일치도" 로 보여 주기엔 공고 쪽 비율이 맞다.
            scored.add(new double[]{Math.min(1.0, dot / docNorm), dot});
            ids.add(d.id());
        }

        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) order.add(i);
        // 같은 비율이면 더 많이(무겁게) 겹친 공고가 앞에 온다.
        order.sort(Comparator.<Integer>comparingDouble(i -> -scored.get(i)[0])
                .thenComparingDouble(i -> -scored.get(i)[1])
                .thenComparing(ids::get));

        final List<Hit> out = new ArrayList<>();
        for (int i = 0; i < Math.min(Math.max(1, topK), order.size()); i++) {
            final int idx = order.get(i);
            out.add(new Hit(ids.get(idx), (float) scored.get(idx)[0]));
        }
        return out;
    }

    /**
     * 이력서가 가리키는 직군. 직군 낱말과 기술 스택 힌트를 세어 가장 많은 것, 그리고 그 절반 이상 나온 것들.
     * (백엔드 이력서에 "디자인 시스템" 한 번 나왔다고 디자인 공고까지 섞지 않으려는 것)
     */
    static Set<String> familiesOf(String text) {
        final Set<String> tokens = tokenize(text);
        final String lower = text.toLowerCase(Locale.ROOT);
        CANON.forEach((alias, canon) -> {
            if (isHangul(alias) && lower.contains(alias)) tokens.add(canon);
        });
        final Map<String, Integer> count = new HashMap<>();
        for (String t : tokens) {
            if (FAMILIES.contains(t)) count.merge(t, 1, Integer::sum);
            for (String f : HINTS.getOrDefault(t, List.of())) count.merge(f, 1, Integer::sum);
        }
        final int max = count.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        final Set<String> out = new LinkedHashSet<>();
        count.forEach((f, c) -> {
            if (max > 0 && c * 2 >= max) out.add(f);
        });
        return out;
    }

    /** 영문 낱말은 통째로만(go ≠ google), 한글은 두 글자 이상이면 이력서 본문 어디에 있어도 맞는다. */
    private static boolean matches(String token, Set<String> queryTokens, String haystack) {
        if (queryTokens.contains(token)) return true;
        return token.length() >= 2 && isHangul(token) && haystack.contains(token);
    }

    private static boolean isHangul(String s) {
        final char c = s.charAt(0);
        return c >= '가' && c <= '힣';
    }

    /** 이력서 쪽 낱말: 조사가 붙은 한글 직군명도 찾고, 기술 스택에서 짐작한 직군을 보탠다. */
    static Set<String> queryTokens(String text) {
        final String lower = text.toLowerCase(Locale.ROOT);
        final Set<String> out = tokenize(lower);
        CANON.forEach((alias, canon) -> {
            if (isHangul(alias) && lower.contains(alias)) out.add(canon);
        });
        for (String t : List.copyOf(out)) {
            final List<String> extra = HINTS.get(t);
            if (extra != null) out.addAll(extra);
        }
        return out;
    }

    static Set<String> tokenize(String text) {
        final Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        final Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            // "spring." 처럼 문장 끝 마침표가 붙어 오는 것을 떼어 낸다(node.js 의 점은 남는다).
            while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
            if (t.length() < 2 && !"c".equals(t) && !"r".equals(t)) continue;
            if (STOPWORDS.contains(t)) continue;
            if (t.chars().allMatch(Character::isDigit)) continue;
            out.add(CANON.getOrDefault(t, t));
        }
        return out;
    }

    static Map<String, Double> idfOf(List<JobDoc> docs) {
        final Map<String, Integer> df = new HashMap<>();
        for (JobDoc d : docs) {
            for (String t : d.tokens()) df.merge(t, 1, Integer::sum);
        }
        final Map<String, Double> out = new HashMap<>(df.size());
        final int n = docs.size();
        df.forEach((t, c) -> out.put(t, Math.log(1.0 + (double) n / c)));
        return out;
    }

    private void ensureLoaded() {
        final LocalDateTime at = loadedAt;
        if (at != null && at.plus(REFRESH).isAfter(LocalDateTime.now())) return;
        synchronized (this) {
            if (loadedAt != null && loadedAt.plus(REFRESH).isAfter(LocalDateTime.now())) return;
            try {
                load();
            } catch (Exception e) {
                // 읽기에 실패해도 앞서 읽어 둔 목록으로 계속 답한다. 다음 요청에서 다시 시도한다.
                log.warn("키워드 매칭용 공고 적재 실패: {}", e.getMessage());
            }
        }
    }

    private void load() {
        final long t0 = System.currentTimeMillis();
        final LocalDateTime now = LocalDateTime.now();
        final List<JobDoc> loaded = new ArrayList<>();
        for (Object[] row : repo.findKeywordRows()) {
            final String endDate = (String) row[6];
            if (isClosed(endDate, now)) continue;
            final StringBuilder b = new StringBuilder();
            // 회사명·근무지는 넣지 않는다 — 이력서에 거의 안 나오면서 공고 쪽 크기만 키워 점수를 흐린다.
            for (int i = 1; i <= 3; i++) {
                if (row[i] != null) b.append(row[i]).append(' ');
            }
            final Set<String> tokens = tokenize(b.toString());
            if (!tokens.isEmpty()) loaded.add(new JobDoc((Long) row[0], tokens));
        }
        docs = List.copyOf(loaded);
        idf = idfOf(loaded);
        loadedAt = now;
        log.info("키워드 매칭용 공고 적재: {}건 ({}ms)", loaded.size(), System.currentTimeMillis() - t0);
    }

    /** 마감일이 지났거나 링크 점검 실패 표시(2000-01-01)인 공고는 뺀다. 상시채용·파싱 불가는 남긴다. */
    static boolean isClosed(String endDate, LocalDateTime now) {
        if (JobEndDates.isAlwaysRecruiting(endDate) || JobEndDates.isCorrupted(endDate)) return false;
        final LocalDateTime end = JobEndDates.parse(endDate);
        return end != null && end.isBefore(now);
    }
}
