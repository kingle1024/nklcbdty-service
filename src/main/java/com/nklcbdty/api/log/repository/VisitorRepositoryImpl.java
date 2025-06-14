package com.nklcbdty.api.log.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;

import com.nklcbdty.api.log.dto.VisitorCountDTO;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import static com.nklcbdty.api.log.entity.QVisitorEntity.*;

@Repository
public class VisitorRepositoryImpl implements VisitorRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VisitorRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<VisitorCountDTO> countsByDate() {
        DateExpression<java.sql.Date> dateTemplate = Expressions.dateTemplate(java.sql.Date.class, "DATE({0})", visitorEntity.insert_dts);

        List<Tuple> results = queryFactory
            .select(dateTemplate, visitorEntity.id.count())
            .from(visitorEntity)
            .groupBy(dateTemplate)
            .orderBy(dateTemplate.desc())
            .fetch();

        return results.stream()
            .map(tuple -> {
                // java.sql.Date sqlDate = tuple.get(dateTemplate);
                LocalDate localDate = Optional.ofNullable(tuple.get(dateTemplate))
                                             .map(java.sql.Date::toLocalDate)
                                             .orElse(null);
                Long countObj = tuple.get(visitorEntity.id.count());
                long count = (countObj != null) ? countObj : 0L;

                return new VisitorCountDTO(localDate, count);
            })
            .collect(Collectors.toList());


    }
}
