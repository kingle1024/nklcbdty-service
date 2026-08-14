package com.nklcbdty.api.crawler.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentTextTest {

    @Test
    void 블록_요소는_줄바꿈으로_끊는다() {
        String html = "<p>주요업무</p><ul><li>서버 개발</li><li>운영</li></ul>";

        String text = ContentText.htmlToText(html);

        // Jsoup.text() 만 쓰면 "주요업무 서버 개발 운영" 한 줄이 된다. 목록이 살아야 읽힌다.
        assertEquals("주요업무\n서버 개발\n운영", text);
    }

    @Test
    void 스크립트와_스타일은_본문에서_뺀다() {
        String html = "<div><script>var a=1;</script><style>.a{color:red}</style><p>자격요건</p></div>";

        assertEquals("자격요건", ContentText.htmlToText(html));
    }

    @Test
    void escape_된_HTML_도_풀어서_읽는다() {
        // Greenhouse 는 본문을 이 모양으로 준다.
        String escaped = "&lt;p&gt;주요업무&lt;/p&gt;&lt;p&gt;백엔드 개발&lt;/p&gt;";

        assertEquals("주요업무\n백엔드 개발", ContentText.htmlToText(escaped));
    }

    @Test
    void 빈_줄과_중복_공백을_정리한다() {
        String html = "<p>가</p><p></p><p></p><p></p><p>나</p>";

        String text = ContentText.htmlToText(html);

        assertFalse(text.contains("\n\n\n"), "빈 줄이 3줄 이상 이어지면 안 된다: " + text.replace("\n", "\\n"));
    }

    @Test
    void 짧은_껍데기_본문은_의미없음으로_본다() {
        // 토스 일부 공고가 실제로 이렇게만 내려온다.
        assertFalse(ContentText.isMeaningful(ContentText.htmlToText("<p>#LI-DNI</p>")));
        assertTrue(ContentText.isMeaningful("가".repeat(ContentText.MIN_MEANINGFUL_LENGTH)));
    }

    @Test
    void null_과_빈_문자열은_빈_본문이다() {
        assertEquals("", ContentText.htmlToText(null));
        assertEquals("", ContentText.htmlToText("   "));
    }
}
