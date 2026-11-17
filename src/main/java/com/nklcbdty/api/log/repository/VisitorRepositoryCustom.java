package com.nklcbdty.api.log.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nklcbdty.api.log.dto.VisitorCountDTO;
import com.nklcbdty.api.log.dto.VisitorSearchCondition;
import com.nklcbdty.api.log.entity.VisitorEntity;

public interface VisitorRepositoryCustom {
    List<VisitorCountDTO> countsByDate();

    /** 최신순 접속 로그 */
    Page<VisitorEntity> search(VisitorSearchCondition condition, Pageable pageable);

    /** 출처(ip_origin)별 건수. 출처 조건(국내만·팀원 제외)은 무시하고 기간·검색어만 적용한다. 출처가 비어 있으면 키는 "UNKNOWN". */
    Map<String, Long> countByOrigin(VisitorSearchCondition condition);
}
