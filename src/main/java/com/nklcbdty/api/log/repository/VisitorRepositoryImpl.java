package com.nklcbdty.api.log.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;

import com.nklcbdty.api.log.dto.VisitorCountDTO;
import com.nklcbdty.api.log.dto.VisitorSearchCondition;
import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.geo.IpOrigin;
import com.querydsl.core.BooleanBuilder;
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

    @Override
    public Page<VisitorEntity> search(VisitorSearchCondition condition, Pageable pageable) {
        final BooleanBuilder where = commonFilter(condition);
        if (condition.isDomesticOnly()) {
            where.and(visitorEntity.ip_origin.in(IpOrigin.DOMESTIC.name(), IpOrigin.INTERNAL.name()));
        }
        if (condition.isExcludeInternal()) {
            // ip_origin 이 NULL 인 행은 != 비교에서 빠지므로 따로 살린다
            where.and(visitorEntity.ip_origin.ne(IpOrigin.INTERNAL.name()).or(visitorEntity.ip_origin.isNull()));
        }

        final List<VisitorEntity> content = queryFactory
            .selectFrom(visitorEntity)
            .where(where)
            .orderBy(visitorEntity.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        final Long total = queryFactory
            .select(visitorEntity.id.count())
            .from(visitorEntity)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Map<String, Long> countByOrigin(VisitorSearchCondition condition) {
        final List<Tuple> rows = queryFactory
            .select(visitorEntity.ip_origin, visitorEntity.id.count())
            .from(visitorEntity)
            .where(commonFilter(condition))
            .groupBy(visitorEntity.ip_origin)
            .fetch();

        final Map<String, Long> counts = new LinkedHashMap<>();
        for (IpOrigin origin : IpOrigin.values()) {
            counts.put(origin.name(), 0L);
        }
        for (Tuple row : rows) {
            final String origin = Optional.ofNullable(row.get(visitorEntity.ip_origin)).orElse(IpOrigin.UNKNOWN.name());
            final Long count = row.get(visitorEntity.id.count());
            counts.merge(origin, count == null ? 0L : count, Long::sum);
        }
        return counts;
    }

    private BooleanBuilder commonFilter(VisitorSearchCondition condition) {
        final BooleanBuilder where = new BooleanBuilder();
        if (condition.getFrom() != null) {
            where.and(visitorEntity.insert_dts.goe(condition.getFrom()));
        }
        if (condition.getTo() != null) {
            where.and(visitorEntity.insert_dts.lt(condition.getTo()));
        }
        final String keyword = condition.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            final String k = keyword.trim();
            where.and(visitorEntity.path.contains(k).or(visitorEntity.insert_ip.contains(k)));
        }
        return where;
    }
}
