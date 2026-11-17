package com.nklcbdty.api.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.admin.dto.AdminVisitorPageResponse;
import com.nklcbdty.api.admin.dto.AdminVisitorRowDto;
import com.nklcbdty.api.log.dto.VisitorSearchCondition;
import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.geo.IpOrigin;
import com.nklcbdty.api.log.repository.VisitorRepository;

/** 관리자 화면의 사용자 접속 로그(visitor) 조회 */
@Service
public class AdminVisitorService {

    static final int MAX_PAGE_SIZE = 200;

    private final VisitorRepository visitorRepository;

    public AdminVisitorService(VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
    }

    /**
     * @param from 이 날짜 0시부터(포함)
     * @param to   이 날짜 끝까지(포함)
     */
    @Transactional(readOnly = true)
    public AdminVisitorPageResponse list(int page, int size, boolean domesticOnly, boolean excludeInternal,
                                         String keyword, LocalDate from, LocalDate to) {
        final int safePage = Math.max(0, page);
        final int safeSize = size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);

        final VisitorSearchCondition condition = VisitorSearchCondition.builder()
            .domesticOnly(domesticOnly)
            .excludeInternal(excludeInternal)
            .keyword(keyword)
            .from(startOf(from))
            .to(to == null ? null : startOf(to.plusDays(1)))
            .build();

        final Page<VisitorEntity> result = visitorRepository.search(condition, PageRequest.of(safePage, safeSize));
        final List<AdminVisitorRowDto> rows = result.getContent().stream()
            .map(AdminVisitorService::toRow)
            .collect(Collectors.toList());

        return AdminVisitorPageResponse.builder()
            .rows(rows)
            .totalElements(result.getTotalElements())
            .totalPages(result.getTotalPages())
            .pageNumber(safePage)
            .pageSize(safeSize)
            .originCounts(visitorRepository.countByOrigin(condition))
            .build();
    }

    static AdminVisitorRowDto toRow(VisitorEntity v) {
        final String origin = v.getIp_origin() == null ? IpOrigin.UNKNOWN.name() : v.getIp_origin();
        return AdminVisitorRowDto.builder()
            .id(v.getId())
            .path(v.getPath())
            .ip(v.getInsert_ip())
            .origin(origin)
            .domestic(isDomestic(origin))
            .countryName(v.getCountry_name())
            .regionName(v.getRegion_name())
            .cityName(v.getCity_name())
            .referer(v.getReferer())
            .acceptLanguage(v.getAccept_language())
            .insertDts(toLocal(v.getInsert_dts()))
            .build();
    }

    private static boolean isDomestic(String origin) {
        try {
            return IpOrigin.valueOf(origin).isDomestic();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // insert_dts 는 JVM 기본 시간대로 쓰고 읽으므로 같은 시간대로 되돌린다
    private static LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Date startOf(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
