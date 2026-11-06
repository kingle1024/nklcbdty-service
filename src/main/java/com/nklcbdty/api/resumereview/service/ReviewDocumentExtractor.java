package com.nklcbdty.api.resumereview.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨삭할 이력서·포트폴리오 파일에서 본문을 꺼낸다. PDF, 텍스트(.txt), 마크다운(.md)을 받는다.
 *
 * <p>이력서 매칭({@code ResumePdfExtractor})과 달리 줄바꿈을 살린다. 첨삭은 "원문의 이 줄을 이렇게"
 * 라고 짚어야 해서, 한 줄로 뭉개면 모델도 사람도 위치를 가리킬 수 없다.</p>
 *
 * <p>파일은 저장하지 않는다. 본문만 꺼내고 바로 버린다.</p>
 */
@Component
public class ReviewDocumentExtractor {

    /** 문서 하나에서 쓰는 최대 글자 수. 이력서·포트폴리오 몇 장 분량이다. */
    static final int MAX_CHARS_PER_DOCUMENT = 15_000;

    public record ReviewDocument(String fileName, String text, boolean truncated) {
    }

    public ReviewDocument extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ResumeReviewException.badInput("빈 파일이 있습니다.");
        }
        final String fileName = file.getOriginalFilename() == null ? "문서" : file.getOriginalFilename();
        final String lower = fileName.toLowerCase(Locale.ROOT);

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ResumeReviewException.badInput(fileName + " 파일을 읽을 수 없습니다.");
        }

        final String raw;
        if (lower.endsWith(".pdf") || "application/pdf".equals(file.getContentType())) {
            raw = pdfText(fileName, bytes);
        } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            raw = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw ResumeReviewException.badInput(
                    fileName + ": PDF, TXT, MD 파일만 올릴 수 있습니다. (Word 문서는 PDF 로 저장해서 올려주세요)");
        }

        final String text = normalize(raw);
        if (text.isBlank()) {
            throw ResumeReviewException.badInput(
                    fileName + "에서 글자를 읽지 못했습니다. (이미지로 된 PDF 는 읽을 수 없습니다)");
        }
        if (text.length() > MAX_CHARS_PER_DOCUMENT) {
            return new ReviewDocument(fileName, text.substring(0, MAX_CHARS_PER_DOCUMENT), true);
        }
        return new ReviewDocument(fileName, text, false);
    }

    private String pdfText(String fileName, byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw ResumeReviewException.badInput(fileName + ": 암호가 걸린 PDF 는 읽을 수 없습니다.");
            }
            final PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            throw ResumeReviewException.badInput(fileName + ": PDF 형식이 올바르지 않습니다.");
        }
    }

    /**
     * 줄은 살리고 줄 안의 공백만 정리한다. 빈 줄은 두 줄 이상 이어지지 않게 한다.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        final String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        final StringBuilder out = new StringBuilder();
        int blankRun = 0;
        for (String line : lines) {
            final String cleaned = line.replaceAll("[\\t\\u00A0\\u3000 ]+", " ").strip();
            if (cleaned.isEmpty()) {
                blankRun++;
                if (blankRun > 1 || out.length() == 0) {
                    continue;
                }
            } else {
                blankRun = 0;
            }
            out.append(cleaned).append('\n');
        }
        return out.toString().strip();
    }
}
