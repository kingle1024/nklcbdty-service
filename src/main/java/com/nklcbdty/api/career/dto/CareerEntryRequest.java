package com.nklcbdty.api.career.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * 관리자 화면의 추가·수정 요청. 목록 칸(성과·기술·태그·링크)은 화면이 다루기 쉬운 배열로 받고,
 * 저장할 때 서비스가 한 칼럼으로 잇는다 — 응답({@link CareerEntryDto})과 같은 모양이다.
 */
@Data
public class CareerEntryRequest {

    private String entryType;
    private String company;
    private String title;
    private String team;
    private String role;
    /** YYYY-MM-DD. 화면은 월까지만 고르므로 1일로 온다 */
    private LocalDate startedOn;
    private LocalDate endedOn;
    private String summary;
    private String description;
    private List<String> achievements;
    private List<String> techStack;
    private List<String> tags;
    /** 한 줄에 하나. "라벨: https://..." 또는 주소만 */
    private List<String> referenceLinks;
}
