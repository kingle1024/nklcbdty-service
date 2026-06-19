package com.nklcbdty.api.ai.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfExtractorTest {

    private final ResumePdfExtractor extractor = new ResumePdfExtractor();

    /** 주어진 줄들을 담은 1페이지 PDF 바이트를 생성한다. (영문/숫자만 — Standard14 폰트 한계) */
    private byte[] makePdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile pdfFile(byte[] bytes) {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
    }

    @Test
    @DisplayName("정상 PDF: 텍스트 추출 + 공백 정규화")
    void extractsAndNormalizes() throws Exception {
        byte[] pdf = makePdf("Backend Engineer", "Spring Boot Kafka Redis", "5 years experience");
        String text = extractor.extract(pdfFile(pdf));

        assertTrue(text.contains("Backend Engineer"), "본문 포함");
        assertTrue(text.contains("Kafka"), "키워드 포함");
        // 개행이 단일 공백으로 정규화되어 연속 공백/줄바꿈이 없어야 함
        assertFalse(text.contains("\n"), "개행 제거됨");
        assertFalse(text.contains("  "), "연속 공백 없음");
    }

    @Test
    @DisplayName("긴 PDF: MAX_CHARS(3000)로 truncate")
    void truncatesLongText() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) sb.append("word").append(i).append(' ');
        byte[] pdf = makePdf(sb.toString());

        String text = extractor.extract(pdfFile(pdf));
        assertEquals(3000, text.length(), "3000자로 잘림");
    }

    @Test
    @DisplayName("빈 파일은 ExtractionException")
    void emptyFileThrows() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        assertThrows(ResumePdfExtractor.ExtractionException.class, () -> extractor.extract(empty));
    }

    @Test
    @DisplayName("PDF가 아닌 바이트는 ExtractionException")
    void notPdfThrows() {
        MockMultipartFile bad = pdfFile("this is not a pdf".getBytes());
        assertThrows(ResumePdfExtractor.ExtractionException.class, () -> extractor.extract(bad));
    }
}
