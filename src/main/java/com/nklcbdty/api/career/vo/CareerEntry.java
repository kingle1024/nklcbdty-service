package com.nklcbdty.api.career.vo;

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
 * 내 경력 기록. 회사별 경력과 경력기술서(프로젝트 단위) 내용을 남긴 것.
 *
 * <p>트러블슈팅 기록과 같은 방식이다 — 표는 로컬의 save-career 스킬이
 * {@code create table if not exists} 로 만들어 쓰고, 이 서비스는 <b>읽기만</b> 한다.
 * 트러블슈팅 표에 카테고리를 덧붙이지 않고 따로 둔 이유는 칸이 맞지 않아서다.
 * 그쪽은 증상·원인·해결이 필수인데, 경력은 기간·소속·역할·성과가 중심이다.
 *
 * <p>한 표에 세 종류가 같이 산다({@link #entryType}).
 * <ul>
 *   <li>{@code company} — 회사 하나. 입사·퇴사일, 부서, 직급</li>
 *   <li>{@code project} — 경력기술서 한 항목. {@link #company} 로 회사 행과 묶인다</li>
 *   <li>그 밖({@code education}, {@code certificate} 등) — 학력·자격증·수상 같은 나머지 이력</li>
 * </ul>
 */
@Entity
@Table(name = "career_entry")
@Getter
public class CareerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사람이 읽는 고유키. 같은 slug 로 다시 저장하면 갱신된다 */
    @Column(nullable = false, length = 150)
    private String slug;

    /** company | project | education | certificate | ... */
    @Column(nullable = false, length = 20)
    private String entryType;

    /** 회사명. 회사 행과 프로젝트 행이 이 값으로 묶인다. 개인 프로젝트·자격증이면 비어 있다 */
    @Column(length = 100)
    private String company;

    /** 회사 행이면 직무 한 줄, 프로젝트 행이면 프로젝트명 */
    @Column(nullable = false, length = 300)
    private String title;

    /** 부서·팀 */
    @Column(length = 100)
    private String team;

    /** 직급이나 프로젝트 안에서 맡은 역할 */
    @Column(length = 100)
    private String role;

    private LocalDate startedOn;

    /** 비어 있으면 재직 중·진행 중 */
    private LocalDate endedOn;

    /** 한두 줄 요약 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 담당 업무와 상세 내용 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 한 줄에 하나씩 적은 성과 */
    @Column(columnDefinition = "TEXT")
    private String achievements;

    /** 쉼표 구분 */
    @Column(length = 300)
    private String techStack;

    /** 쉼표 구분 kebab-case */
    @Column(length = 300)
    private String tags;

    /** 한 줄에 하나씩 들어 있는 참고 주소 */
    @Column(columnDefinition = "TEXT")
    private String referenceLinks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime updatedAt;
}
