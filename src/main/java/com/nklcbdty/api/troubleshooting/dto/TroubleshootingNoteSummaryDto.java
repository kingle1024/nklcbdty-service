package com.nklcbdty.api.troubleshooting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.nklcbdty.api.troubleshooting.vo.TroubleshootingNote;

import lombok.Builder;
import lombok.Getter;

/**
 * 목록 화면용 요약. 본문(증상·원인·해결·검증)은 담지 않는다 — 기록 하나가 수천 자라
 * 목록에 다 실으면 응답이 커진다. 대신 한 줄로 남긴 {@code lesson} 을 미리보기로 준다.
 */
@Builder
@Getter
public class TroubleshootingNoteSummaryDto {

    private Long id;
    private String slug;
    private LocalDate occurredOn;
    private String project;
    private String component;
    private String title;
    private String severity;
    private String errorCode;
    private String lesson;
    private List<String> tags;
    private List<String> techStack;
    /** 참고 링크가 있는지. 목록에 아이콘만 띄우고 주소는 상세에서 보여준다 */
    private boolean hasReferences;
    private LocalDateTime updatedAt;

    public static TroubleshootingNoteSummaryDto from(TroubleshootingNote note) {
        return TroubleshootingNoteSummaryDto.builder()
            .id(note.getId())
            .slug(note.getSlug())
            .occurredOn(note.getOccurredOn())
            .project(note.getProject())
            .component(note.getComponent())
            .title(note.getTitle())
            .severity(note.getSeverity())
            .errorCode(note.getErrorCode())
            .lesson(note.getLesson())
            .tags(TroubleshootingTextParts.splitByComma(note.getTags()))
            .techStack(TroubleshootingTextParts.splitByComma(note.getTechStack()))
            .hasReferences(!TroubleshootingTextParts.splitLinks(note.getReferenceLinks()).isEmpty())
            .updatedAt(note.getUpdatedAt())
            .build();
    }
}
