package com.nklcbdty.api.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 의미 검색/이력서 매칭이 쓰는 임베딩 진입점.
 *
 * <p>실제 벡터 생성은 {@link EmbeddingProvider} 구현체에 위임한다
 * ({@code nklcb.rag.embedding.provider} = {@code openai} | {@code local} | {@code none}).
 * 공급자별로 조건부 빈이라 선택된 것 하나만 올라오고, {@code none} 이면 하나도 없다.</p>
 *
 * <p>정규화는 여기서만 한다 — {@link JobEmbeddingCache} 가 단위벡터 가정으로
 * 내적을 코사인 유사도로 쓰기 때문에, 저장되는 벡터와 질의 벡터가 같은 경로를 타야 한다.
 * (OpenAI 는 차원을 줄이면 정규화되지 않은 벡터를 주므로 이 단계가 반드시 필요하다)</p>
 */
@Slf4j
@Service
public class EmbeddingService {

    /** 선택된 공급자. {@code provider=none} 이면 null. */
    private final EmbeddingProvider provider;

    public EmbeddingService(ObjectProvider<EmbeddingProvider> providers) {
        this.provider = providers.getIfAvailable();
        if (this.provider == null) {
            log.info("임베딩 공급자 없음(nklcb.rag.embedding.provider=none) → 의미 검색/이력서 매칭 미동작");
        }
    }

    public boolean isAvailable() {
        return provider != null && provider.isAvailable();
    }

    /** {@code job_mst.embedding_version} 에 기록할 현재 모델 식별자. */
    public String modelVersion() {
        return provider == null ? "none" : provider.modelVersion();
    }

    /** 입력 텍스트를 단위벡터로 임베딩한다. 실패/비가용 시 null. */
    public float[] embed(String text) {
        if (!isAvailable() || text == null || text.isBlank()) return null;
        float[] vec = provider.embed(text);
        return vec == null ? null : Vectors.normalize(vec);
    }

    /**
     * 여러 텍스트를 한 번에 단위벡터로 임베딩한다.
     * 입력과 같은 크기의 리스트를 돌려주고, 개별 실패는 그 위치에 null 을 담는다.
     * 비가용 시 빈 리스트.
     */
    public List<float[]> embedAll(List<String> texts) {
        if (!isAvailable() || texts == null || texts.isEmpty()) return List.of();

        List<float[]> raw = provider.embedAll(texts);
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            float[] vec = i < raw.size() ? raw.get(i) : null;
            out.add(vec == null ? null : Vectors.normalize(vec));
        }
        return out;
    }
}
