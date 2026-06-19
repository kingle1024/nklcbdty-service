package com.nklcbdty.api.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 업로드된 이력서 PDF에서 텍스트를 추출한다.
 *
 * <p>추출된 텍스트는 의미 검색({@link SemanticSearchService})의 query 로 사용된다.
 * 임베딩 모델(paraphrase-multilingual-MiniLM-L12-v2)은 긴 입력일수록 벡터가 희석되므로
 * 앞부분 일부만 사용하도록 길이를 제한한다.</p>
 */
@Slf4j
@Service
public class ResumePdfExtractor {

    /** 임베딩에 넘길 최대 문자 수. 모델 토큰 한계(~512)와 매칭 정확도를 고려한 보수적 상한. */
    private static final int MAX_CHARS = 3000;

    public static final class ExtractionException extends RuntimeException {
        public ExtractionException(String message) {
            super(message);
        }
    }

    /**
     * PDF MultipartFile → 정규화된 평문 텍스트.
     *
     * @throws ExtractionException 빈 파일/암호화/파싱 실패/본문 없음
     */
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExtractionException("PDF 파일이 비어 있습니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ExtractionException("PDF 파일을 읽을 수 없습니다.");
        }

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw new ExtractionException("암호화된 PDF는 처리할 수 없습니다.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String raw = stripper.getText(doc);
            String normalized = normalize(raw);
            if (normalized.isBlank()) {
                // 스캔 이미지 PDF 등 텍스트 레이어가 없는 경우
                throw new ExtractionException("PDF에서 텍스트를 추출하지 못했습니다. (이미지 기반 PDF일 수 있습니다)");
            }
            return truncate(normalized);
        } catch (IOException e) {
            log.warn("PDF 파싱 실패: {}", e.getMessage());
            throw new ExtractionException("PDF 형식이 올바르지 않습니다.");
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        // 연속 공백/개행을 단일 공백으로 압축
        return text.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    private String truncate(String text) {
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS);
    }
}
