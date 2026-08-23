package com.nklcbdty.api.troubleshooting.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/**
 * 목록 응답. 페이지 정보 이름은 게시판({@code BoardPageResponse})과 같게 맞춰 뒀다 —
 * 프론트의 페이징 코드를 그대로 쓸 수 있게 하려는 것.
 *
 * <p>필터 후보(프로젝트/태그)와 심각도 집계는 목록과 같은 응답에 실어 보낸다. 기록이
 * 수십 건 규모라 따로 API 를 왕복할 이유가 없고, <b>검색 조건과 무관하게 전체 기준</b>으로
 * 계산한다. 필터를 걸 때마다 선택지가 사라지면 다른 조건으로 갈아탈 수 없기 때문이다.
 */
@Builder
@Getter
public class TroubleshootingPageResponse {

    private List<TroubleshootingNoteSummaryDto> rows;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;

    /** 전체 기록 수(필터 적용 전). 화면 상단 요약에 쓴다 */
    private long totalNotes;
    /** 필터 드롭다운용 프로젝트 이름. 기록이 많은 순 */
    private List<String> projects;
    /** 필터용 태그. 많이 쓰인 순으로 잘라 보낸다 */
    private List<String> tags;
    /** 심각도 → 건수. 값이 비어 있는 기록은 세지 않는다 */
    private Map<String, Long> severityCounts;
}
