package com.nklcbdty.api.user.repository;

import java.util.List;

import org.springframework.stereotype.Repository;
import com.querydsl.core.types.Expression;
import com.nklcbdty.api.user.vo.QUserInterestVo;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserInterestRepositoryImpl implements UserInterestRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QUserInterestVo userInterest = QUserInterestVo.userInterestVo;

    @Override
    public List<Tuple> findGroupedByUserTypeAndUserId() {
        return queryFactory
            .select(userInterest.itemType, userInterest.userId)
            .from(userInterest)
            .groupBy(userInterest.itemType, userInterest.userId)
            .fetch();
    }

    public List<Tuple> findUserCategories() {
            // CASE WHEN 로직을 QueryDSL 표현식으로 정의
        CaseBuilder caseBuilder = new CaseBuilder();

        // 각 user_id별로 company 타입과 career 타입 존재 여부를 나타내는 표현식
        // MAX(CASE WHEN itemType = 'company' THEN 1 ELSE 0 END)
        // 타입을 NumberExpression<Integer>로 변경!
        NumberExpression<Integer> hasCompany = caseBuilder
                .when(userInterest.itemType.eq("company")).then(1)
                .otherwise(0)
                .max(); // 그룹별 최대값 (하나라도 1이면 1)

        // MAX(CASE WHEN itemType = 'career' THEN 1 ELSE 0 END)
        // 타입을 NumberExpression<Integer>로 변경!
        NumberExpression<Integer> hasCareer = caseBuilder
                .when(userInterest.itemType.eq("job")).then(1)
                .otherwise(0)
                .max(); // 그룹별 최대값 (하나라도 1이면 1)

        // 최종 카테고리를 결정하는 CASE WHEN 표현식
        Expression<String> userCategory = caseBuilder
                .when(hasCompany.eq(1).and(hasCareer.eq(1))).then("AB")
                .when(hasCompany.eq(1).and(hasCareer.eq(0))).then("onlyA")
                .when(hasCompany.eq(0).and(hasCareer.eq(1))).then("onlyB")
                .otherwise("nothing"); // 필요하다면 다른 경우도 추가

        return queryFactory
            .select(
                    userInterest.userId, // userId 선택
                    userCategory // 위에서 정의한 카테고리 표현식 선택
            )
            .from(userInterest) // UserInterestVo 엔티티 사용
            .groupBy(userInterest.userId) // userId로 그룹화
            // HAVING 절: A 또는 B 중 하나라도 해당하는 유저만 필터링
            .having(hasCompany.eq(1).or(hasCareer.eq(1)))
            .fetch(); // 쿼리 실행 및 결과 가져오기
    }
}
