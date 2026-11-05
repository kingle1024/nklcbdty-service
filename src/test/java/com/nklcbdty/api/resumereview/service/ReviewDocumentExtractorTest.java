package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ReviewDocumentExtractorTest {

    private final ReviewDocumentExtractor extractor = new ReviewDocumentExtractor();

    static byte[] pdfWithLines(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = 700;
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(72, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 20;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("PDF 는 줄을 살려서 읽는다 — 첨삭이 줄 단위로 위치를 짚는다")
    void pdfKeepsLines() throws Exception {
        byte[] pdf = pdfWithLines("Experience", "Order API   development", "Skills: Java");

        ReviewDocumentExtractor.ReviewDocument d = extractor.extract(
                new MockMultipartFile("files", "resume.pdf", "application/pdf", pdf));

        assertThat(d.text()).isEqualTo("Experience\nOrder API development\nSkills: Java");
        assertThat(d.truncated()).isFalse();
    }

    @Test
    @DisplayName("TXT/MD 는 UTF-8 로 읽고, 빈 줄은 하나로 줄인다")
    void textFiles() {
        String md = "# 홍길동\r\n\r\n\r\n\r\n## 경력\t\t백엔드\n";
        ReviewDocumentExtractor.ReviewDocument d = extractor.extract(
                new MockMultipartFile("files", "resume.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8)));

        assertThat(d.text()).isEqualTo("# 홍길동\n\n## 경력 백엔드");
    }

    @Test
    @DisplayName("너무 긴 문서는 자르고 잘렸다고 표시한다")
    void truncates() {
        String longText = "가".repeat(ReviewDocumentExtractor.MAX_CHARS_PER_DOCUMENT + 10);
        ReviewDocumentExtractor.ReviewDocument d = extractor.extract(
                new MockMultipartFile("files", "a.txt", "text/plain", longText.getBytes(StandardCharsets.UTF_8)));

        assertThat(d.text()).hasSize(ReviewDocumentExtractor.MAX_CHARS_PER_DOCUMENT);
        assertThat(d.truncated()).isTrue();
    }

    @Test
    @DisplayName("깨진 PDF, 글자 없는 파일은 이유를 알려 준다")
    void badFiles() {
        assertThatThrownBy(() -> extractor.extract(
                new MockMultipartFile("files", "x.pdf", "application/pdf", "not a pdf".getBytes())))
                .hasMessageContaining("PDF 형식");
        assertThatThrownBy(() -> extractor.extract(
                new MockMultipartFile("files", "blank.txt", "text/plain", " \n \n".getBytes())))
                .hasMessageContaining("글자를 읽지 못했습니다");
    }
}
