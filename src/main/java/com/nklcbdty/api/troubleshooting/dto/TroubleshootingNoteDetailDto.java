package com.nklcbdty.api.troubleshooting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts.ReferenceLink;
import com.nklcbdty.api.troubleshooting.vo.TroubleshootingNote;

import lombok.Builder;
import lombok.Getter;

/** 상세 화면용. 저장된 본문을 손대지 않고 그대로 내려준다(에러 메시지 원문이 검색 키다) */
@Builder
@Getter
public class TroubleshootingNoteDetailDto {

    private Long id;
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
    private List<ReferenceLink> referenceLinks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TroubleshootingNoteDetailDto from(TroubleshootingNote note) {
        return TroubleshootingNoteDetailDto.builder()
            .id(note.getId())
            .slug(note.getSlug())
            .occurredOn(note.getOccurredOn())
            .project(note.getProject())
            .component(note.getComponent())
            .title(note.getTitle())
            .severity(note.getSeverity())
            .errorCode(note.getErrorCode())
            .symptom(note.getSymptom())
            .rootCause(note.getRootCause())
            .resolution(note.getResolution())
            .verification(note.getVerification())
            .prevention(note.getPrevention())
            .lesson(note.getLesson())
            .techStack(TroubleshootingTextParts.splitByComma(note.getTechStack()))
            .tags(TroubleshootingTextParts.splitByComma(note.getTags()))
            .referenceLinks(TroubleshootingTextParts.splitLinks(note.getReferenceLinks()))
            .createdAt(note.getCreatedAt())
            .updatedAt(note.getUpdatedAt())
            .build();
    }
}
