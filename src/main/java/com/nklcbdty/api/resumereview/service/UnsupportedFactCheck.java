package com.nklcbdty.api.resumereview.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 고친 글(after)에 지원자 문서 어디에도 없는 사실이 끼어들었는지 본다.
 *
 * <p>프롬프트로 "지어내지 마라" 고 해도 모델은 공고 우대사항을 맞추려고 없는 경험을 끼워 넣는다
 * (2026-11-09 운영: "주문 API 서버 개발" → "…및 MSA 환경 구축 참여" — 이력서엔 MSA 가 없고 공고 우대사항이었다).
 * 지원자가 그대로 내면 허위 기재가 되므로, 서버에서 한 번 더 걸러 자동 적용하지 않고 제안으로만 남긴다.</p>
 *
 * <p>잡는 것은 확실한 두 가지뿐이다. 한글 표현 바꾸기("참여" → "수행")는 정상 첨삭과 구별이 안 돼서 보지 않는다.</p>
 * <ul>
 *   <li>숫자(수치·기간·퍼센트) — 원문에 없는 숫자는 지어낸 성과다</li>
 *   <li>영문 낱말(기술명·약어) — 원문에 없고, 공고에 있거나 약어이거나 한글 문장 속에 끼어든 것</li>
 * </ul>
 * <p>{@code [직접 입력: …]} 자리표시 안은 지원자가 채울 칸이라 보지 않는다.</p>
 */
public final class UnsupportedFactCheck {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]*직접\\s*입력[^\\]]*\\]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z][A-Za-z0-9+#]*(?:\\.[A-Za-z0-9]+)*");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    private UnsupportedFactCheck() {
    }

    /**
     * @param after   모델이 고친 글
     * @param source  지원자 문서 전체(문서 안 다른 곳의 사실을 끌어오는 것은 허용한다)
     * @param posting 채용 공고
     * @return 원문에 근거가 없는 낱말들. 없으면 빈 집합
     */
    public static Set<String> unsupported(String after, String source, String posting) {
        final Set<String> out = new LinkedHashSet<>();
        if (after == null || after.isBlank()) {
            return out;
        }
        final String text = PLACEHOLDER.matcher(after).replaceAll(" ");
        final String src = source == null ? "" : source.toLowerCase(Locale.ROOT);
        final String compactSrc = src.replaceAll("[\\s,]", "");
        final String post = posting == null ? "" : posting.toLowerCase(Locale.ROOT);
        final boolean koreanSentence = HANGUL.matcher(text).find();

        final Matcher n = NUMBER.matcher(text);
        while (n.find()) {
            final String num = n.group().replace(",", "");
            if (!compactSrc.contains(num)) {
                out.add(n.group());
            }
        }

        final Matcher m = LATIN.matcher(text);
        while (m.find()) {
            final String word = m.group();
            final String lower = word.toLowerCase(Locale.ROOT);
            if (lower.length() < 2 || containsWord(src, lower)) {
                continue;
            }
            final boolean acronym = word.length() >= 2 && word.equals(word.toUpperCase(Locale.ROOT));
            if (koreanSentence || acronym || containsWord(post, lower)) {
                out.add(word);
            }
        }
        return out;
    }

    /** 영문은 낱말 경계로 찾는다(원문 "Javascript" 가 "Java" 를 정당화하지 않게). */
    private static boolean containsWord(String haystack, String word) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(word) + "(?![a-z0-9])").matcher(haystack).find();
    }
}
