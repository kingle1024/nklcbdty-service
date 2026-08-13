package com.nklcbdty.api.ai.rag;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 한국어/영어 다국어 문장 임베딩 서비스 (DJL + PyTorch + HuggingFace).
 *
 * <p>모델: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 (384차원, ~117MB)</p>
 * <p>첫 부팅 시 모델을 자동 다운로드해 {@code ~/.djl.ai/cache/} 에 캐싱한다.</p>
 *
 * <p>모델 로드 실패 시(네트워크 없음, 메모리/디스크 부족 등) 앱은 정상 기동되며,
 * {@link #isAvailable()} 가 false 를 반환해 호출자가 graceful fallback 할 수 있도록 한다.</p>
 *
 * <p>모델 + PyTorch 네이티브 런타임은 수백 MB 를 쓴다. 메모리/디스크가 작은 환경
 * (예: 0.5GB 컨테이너)에서는 {@code nklcb.rag.embedding.enabled=false} 로 두어
 * 아예 시도하지 않는 편이 낫다. 기본값이 false 인 이유다.</p>
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL_URL =
            "djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
    public static final String MODEL_VERSION = "paraphrase-multilingual-MiniLM-L12-v2";

    private final boolean enabled;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public EmbeddingService(@Value("${nklcb.rag.embedding.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @PostConstruct
    public void load() {
        if (!enabled) {
            log.info("임베딩 모델 로딩 비활성화(nklcb.rag.embedding.enabled=false) → 의미 검색/이력서 매칭 미동작");
            return;
        }
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
        } catch (Throwable t) {
            // Exception 이 아니라 Throwable 로 받는다.
            // 메모리 부족(OutOfMemoryError)·네이티브 라이브러리 로드 실패(UnsatisfiedLinkError,
            // NoClassDefFoundError)는 Error 라서 catch(Exception) 에 안 걸린다.
            // 그대로 전파되면 Spring 컨텍스트가 죽어 앱 자체가 기동하지 못한다.
            // (실제로 0.5GB 컨테이너에서 이 문제로 배포가 계속 실패했다)
            // 여기서 잡아야 "모델 없이도 나머지 기능은 뜬다"는 이 클래스의 계약이 지켜진다.
            log.error("임베딩 모델 로드 실패 → 의미 검색 비활성화. 원인: {}", t.toString());
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
