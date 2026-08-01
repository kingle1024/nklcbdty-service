package com.nklcbdty.api.crawler.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.common.CacheConfig;
import com.nklcbdty.api.crawler.vo.CategoryDtl;
import com.nklcbdty.api.crawler.vo.CategoryMst;
import com.querydsl.jpa.impl.JPAQueryFactory;

import static com.nklcbdty.api.crawler.vo.QCategoryMst.*;

@Service
public class CategoryService {

    private final JPAQueryFactory queryFactory;
    private final ObjectMapper objectMapper;

    @Autowired
    public CategoryService(JPAQueryFactory queryFactory, ObjectMapper objectMapper) {
        this.queryFactory = queryFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code /api/category/list} 응답 JSON.
     *
     * <p>파라미터가 없어 캐시 키가 하나뿐이라 상수 키를 쓴다. 카테고리는 크롤과 무관하게
     * 거의 바뀌지 않으므로 별도 무효화 없이 TTL 만료에만 의존한다.</p>
     */
    @Cacheable(cacheNames = CacheConfig.CATEGORY_LIST, key = "'all'")
    public String getAllCategoriesOrderedByRankAsJson() {
        try {
            return objectMapper.writeValueAsString(getAllCategoriesOrderedByRank());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카테고리 목록 JSON 직렬화 실패", e);
        }
    }

    public List<CategoryMst> getAllCategoriesOrderedByRank() {
        List<CategoryMst> mstList = queryFactory.selectFrom(categoryMst)
                .orderBy(categoryMst.rank.asc())
                .fetch();

        // 각 CategoryMst의 categoryDtls를 rank로 정렬
        mstList.forEach(mst -> {
            List<CategoryDtl> sortedDtls = mst.getCategoryDtls().stream()
                    .sorted(Comparator.comparing(CategoryDtl::getRank))
                    .collect(Collectors.toList());
            mst.getCategoryDtls().clear(); // 기존 리스트를 비우고
            mst.getCategoryDtls().addAll(sortedDtls); // 정렬된 리스트 추가
        });

        return mstList;
    }
}
