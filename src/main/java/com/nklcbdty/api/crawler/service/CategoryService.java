package com.nklcbdty.api.crawler.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.vo.CategoryDtl;
import com.nklcbdty.api.crawler.vo.CategoryMst;
import com.querydsl.jpa.impl.JPAQueryFactory;

import static com.nklcbdty.api.crawler.vo.QCategoryMst.*;

@Service
public class CategoryService {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public CategoryService(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
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
