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
 * <p>쓰는 길이 둘이다. 로컬의 save-career 스킬이 DB 에 바로 넣고(표도 그쪽이
 * {@code create table if not exists} 로 만든다), 관리자 화면이 이 서비스를 거쳐
 * 추가·수정·삭제한다. 그래서 {@code ddl-auto=none} 에 의존하고, 칼럼이 늘면
 * 스킬의 DDL 이 먼저 바뀌고 이 엔티티가 따라온다.
 *
 * <p>트러블슈팅 표에 카테고리를 덧붙이지 않고 따로 둔 이유는 칸이 맞지 않아서다.
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

    /** 사람이 읽는 고유키. 스킬이 같은 slug 로 다시 저장하면 갱신된다. 화면에서 고쳐도 바꾸지 않는다 */
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

    /**
     * 두 시각은 DB 가 채운다(default current_timestamp, updated_at 은 on update 까지).
     * JPA 가 null 을 넣으면 not null 에 걸리므로 insert·update 대상에서 뺀다.
     * columnDefinition 은 테스트(H2, ddl-auto=create)에서 표를 만들 때만 쓰인다.
     */
    @Column(nullable = false, insertable = false, updatable = false,
        columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(nullable = false, insertable = false, updatable = false,
        columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    /** 새 항목. slug 는 만든 뒤 바꾸지 않는다 */
    public static CareerEntry create(String slug) {
        CareerEntry entry = new CareerEntry();
        entry.slug = slug;
        return entry;
    }

    /** 화면에서 고칠 수 있는 칸을 통째로 바꾼다. 값 검사·정리는 서비스가 끝낸 뒤 부른다 */
    public void apply(String entryType, String company, String title, String team, String role,
                      LocalDate startedOn, LocalDate endedOn, String summary, String description,
                      String achievements, String techStack, String tags, String referenceLinks) {
        this.entryType = entryType;
        this.company = company;
        this.title = title;
        this.team = team;
        this.role = role;
        this.startedOn = startedOn;
        this.endedOn = endedOn;
        this.summary = summary;
        this.description = description;
        this.achievements = achievements;
        this.techStack = techStack;
        this.tags = tags;
        this.referenceLinks = referenceLinks;
    }
}
