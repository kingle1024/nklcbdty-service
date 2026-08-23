package com.nklcbdty.api.troubleshooting.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 트러블슈팅 기록. 해결한 장애를 나중에 글로 옮길 수 있는 형태로 남긴 것.
 *
 * <p>이 표는 이 서비스가 만들지 않는다. 로컬의 save-troubleshooting 스킬이
 * {@code create table if not exists} 로 만들어 쓰고 있고, 여기서는 <b>읽기만</b> 한다.
 * 그래서 setter 를 두지 않고({@code @Getter} 만) {@code ddl-auto=none} 에 의존한다.
 * 칼럼이 늘어나면 스킬의 DDL 이 먼저 바뀌고 이 엔티티가 따라온다.
 *
 * <p>{@code travel} 스키마는 다른 프로젝트와 공유하므로 표 이름을 바꾸거나 지우지 않는다.
 */
@Entity
@Table(name = "troubleshooting_note")
@Getter
public class TroubleshootingNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사람이 읽는 고유키. 상세 화면의 주소로 그대로 쓴다 */
    @Column(nullable = false, length = 150)
    private String slug;

    /** 장애 발생일. 기록한 날이 아니다 */
    @Column(nullable = false)
    private LocalDate occurredOn;

    @Column(nullable = false, length = 100)
    private String project;

    /** 어느 구성요소에서 터졌는지 */
    @Column(length = 200)
    private String component;

    @Column(nullable = false, length = 300)
    private String title;

    /** low | medium | high | critical */
    @Column(length = 20)
    private String severity;

    /** SQLSTATE, HTTP status, 예외 이름 등 검색 가능한 식별자 */
    @Column(length = 50)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String symptom;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(columnDefinition = "TEXT")
    private String verification;

    @Column(columnDefinition = "TEXT")
    private String prevention;

    @Column(columnDefinition = "TEXT")
    private String lesson;

    /** 쉼표 구분 */
    @Column(length = 300)
    private String techStack;

    /** 쉼표 구분 kebab-case */
    @Column(length = 300)
    private String tags;

    /** 한 줄에 하나씩 들어 있는 PR/커밋/CI 런 주소 */
    @Column(columnDefinition = "TEXT")
    private String referenceLinks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime updatedAt;
}
