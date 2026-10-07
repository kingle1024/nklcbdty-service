package com.nklcbdty.api.troubleshooting.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * 관리자 화면의 추가·수정 요청. 목록 칸(기술·태그·링크)은 배열로 받고 저장할 때 한 칼럼으로 잇는다 —
 * 응답({@link TroubleshootingNoteDetailDto})과 같은 모양이다.
 */
@Data
public class TroubleshootingNoteRequest {

    /** 추가할 때만 쓴다. 비우면 서버가 만든다. 수정 때는 무시한다(상세 화면 주소라서) */
    private String slug;
    private LocalDate occurredOn;
    private String project;
    private String component;
    private String title;
    private String severity;
    private String errorCode;
    private String symptom;
    private String rootCause;
    private String resolution;
    private String verification;
    private String prevention;
    private String lesson;
    private List<String> techStack;
    private List<String> tags;
    /** 한 줄에 하나. "라벨: https://..." 또는 주소만 */
    private List<String> referenceLinks;
}
