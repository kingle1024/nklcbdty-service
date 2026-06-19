package com.nklcbdty.api.ai.rag;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국어/영어 다국어 문장 임베딩 서비스 (DJL + PyTorch + HuggingFace).
 *
 * <p>모델: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 (384차원, ~117MB)</p>
 * <p>첫 부팅 시 모델을 자동 다운로드해 {@code ~/.djl.ai/cache/} 에 캐싱한다.</p>
 *
 * <p>모델 로드 실패 시(네트워크 없음 등) 앱은 정상 기동되며,
 * {@link #isAvailable()} 가 false 를 반환해 호출자가 graceful fallback 할 수 있도록 한다.</p>
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL_URL =
            "djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
    public static final String MODEL_VERSION = "paraphrase-multilingual-MiniLM-L12-v2";

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    @PostConstruct
    public void load() {
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls(MODEL_URL)
                    .optEngine("PyTorch")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build();
            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();
            log.info("임베딩 모델 로드 완료: {}", MODEL_VERSION);
        } catch (Exception e) {
            log.error("임베딩 모델 로드 실패 → 의미 검색 비활성화. 원인: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }

    public boolean isAvailable() {
        return predictor != null;
    }

    /** 입력 텍스트를 단위벡터로 임베딩한다. 실패/비가용 시 null. */
    public float[] embed(String text) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return null;
        }
        try {
            float[] vec;
            synchronized (predictor) {
                vec = predictor.predict(text);
            }
            return Vectors.normalize(vec);
        } catch (TranslateException e) {
            log.error("임베딩 실패: {}", e.getMessage());
            return null;
        }
    }
}
