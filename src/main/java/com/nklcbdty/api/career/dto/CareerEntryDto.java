package com.nklcbdty.api.career.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.nklcbdty.api.career.vo.CareerEntry;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingTextParts.ReferenceLink;

import lombok.Builder;
import lombok.Getter;

/** 경력 항목 하나. 본문은 손대지 않고 내려주고, 이어붙은 값만 목록으로 푼다 */
@Builder
@Getter
public class CareerEntryDto {

    private Long id;
    private String slug;
    private String entryType;
    private String company;
    private String title;
    private String team;
    private String role;
    private LocalDate startedOn;
    private LocalDate endedOn;
    private String summary;
    private String description;
    private List<String> achievements;
    private List<String> techStack;
    private List<String> tags;
    private List<ReferenceLink> referenceLinks;
    private LocalDateTime updatedAt;

    public static CareerEntryDto from(CareerEntry entry) {
        return CareerEntryDto.builder()
            .id(entry.getId())
            .slug(entry.getSlug())
            .entryType(entry.getEntryType())
            .company(entry.getCompany())
            .title(entry.getTitle())
            .team(entry.getTeam())
            .role(entry.getRole())
            .startedOn(entry.getStartedOn())
            .endedOn(entry.getEndedOn())
            .summary(entry.getSummary())
            .description(entry.getDescription())
            .achievements(splitLines(entry.getAchievements()))
            .techStack(TroubleshootingTextParts.splitByComma(entry.getTechStack()))
            .tags(TroubleshootingTextParts.splitByComma(entry.getTags()))
            .referenceLinks(TroubleshootingTextParts.splitLinks(entry.getReferenceLinks()))
            .updatedAt(entry.getUpdatedAt())
            .build();
    }

    /** 성과는 한 줄에 하나. 붙여 넣으며 딸려 온 글머리표("- ", "• ")는 화면이 다시 붙이므로 뗀다 */
    static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
            .map(s -> s.trim().replaceFirst("^[-*•·]\\s*", ""))
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
