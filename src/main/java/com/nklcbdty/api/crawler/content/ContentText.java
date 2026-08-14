package com.nklcbdty.api.crawler.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * 본문 문자열 손질. 회사마다 HTML 이든 escape 된 HTML 이든 내려오는 모양이 달라 여기서 한 번에 정규화한다.
 */
public final class ContentText {

    /** 본문이 이보다 짧으면 "못 가져온 것"으로 본다. #LI-DNI 같은 껍데기 응답을 거르기 위함. */
    public static final int MIN_MEANINGFUL_LENGTH = 80;

    private ContentText() {
    }

    /**
     * HTML 을 읽을 수 있는 텍스트로 바꾼다.
     *
     * <p>{@code Jsoup.text()} 는 블록 요소를 공백 하나로 이어붙여 문단·목록이 전부 한 줄이 된다.
     * 본문은 "자격요건 / 우대사항" 같은 목록이 핵심이라 줄바꿈을 살려야 읽히고, 임베딩에도 낫다.</p>
     */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(unescapeIfNeeded(html));
        doc.select("script, style, noscript").remove();
        doc.select("br").append("\\n");
        doc.select("p, div, li, tr, h1, h2, h3, h4, h5, h6").append("\\n");
        String text = doc.text().replace("\\n", "\n");
        return normalize(text);
    }

    /** 이미 텍스트인 값(줄바꿈 있는 평문)을 손질한다. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace(' ', ' ')      // &nbsp;
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\n *", "\n")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    /**
     * Greenhouse 는 본문을 escape 된 HTML(&amp;lt;p&amp;gt;...)로 준다.
     * 태그가 안 보이고 escape 만 보이면 한 번 풀어준 뒤 파싱한다.
     */
    private static String unescapeIfNeeded(String html) {
        boolean looksEscaped = html.contains("&lt;") && !html.contains("<p") && !html.contains("<div");
        return looksEscaped ? Parser.unescapeEntities(html, false) : html;
    }

    public static boolean isMeaningful(String text) {
        return text != null && text.strip().length() >= MIN_MEANINGFUL_LENGTH;
    }
}
