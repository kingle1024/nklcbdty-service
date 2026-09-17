package com.nklcbdty.api.ai.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 임베딩 벡터를 만들어 주는 공급자. 로컬 모델(DJL)과 외부 API 를 갈아끼울 수 있도록 분리했다.
 *
 * <p>구현체는 {@code nklcb.rag.embedding.provider} 값에 따라 하나만 빈으로 올라오고,
 * {@link EmbeddingService} 가 그것을 찾아 위임한다.</p>
 *
 * <p>여기서 돌려주는 벡터는 정규화되지 않은 raw 값이다 — 단위벡터 정규화는
 * {@link EmbeddingService} 가 한 곳에서 책임진다.</p>
 */
public interface EmbeddingProvider {

    /** 임베딩을 실제로 만들 수 있는 상태인지. (API 키 미설정, 모델 로드 실패 등이면 false) */
    boolean isAvailable();

    /**
     * {@code job_mst.embedding_version} 에 기록되는 식별자.
     * 모델이나 차원이 바뀌면 이 값이 달라져야 한다 — 기존 벡터가 자동으로 재인덱싱 대상이 된다.
     */
    String modelVersion();

    /** 실패 시 null. */
    float[] embed(String text);

    /**
     * 여러 텍스트를 한 번에 임베딩한다.
     *
     * <p>입력과 같은 크기의 리스트를 돌려주며, 개별 실패는 그 위치에 null 을 담는다.
     * 아무것도 못 만들었으면 빈 리스트를 줄 수 있다.</p>
     */
    default List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) out.add(embed(t));
        return out;
    }
}
