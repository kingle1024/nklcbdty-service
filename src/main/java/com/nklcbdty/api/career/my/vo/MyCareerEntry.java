package com.nklcbdty.api.career.my.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.nklcbdty.api.career.service.CareerInput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 마이페이지에서 사용자가 직접 적는 경력 한 줄.
 *
 * <p>관리자 경력({@code career_entry}, 사이트 운영자 한 사람의 이력)과 칸은 같지만 표를 나눴다.
 * 그쪽은 로컬 save-career 스킬이 slug 로 덮어쓰는 표라 사용자 구분 칸을 끼워 넣으면 스킬이 깨지고,
 * 무엇보다 개인 이력이 한 표에 섞여 있으면 조회 조건 하나 빠뜨리는 순간 남의 경력이 나간다.
 *
 * <p>모든 조회·수정·삭제는 {@link #userId} 로 한정한다(나의 캘린더와 같은 방식).
 */
@Entity
@Table(name = "my_career_entry")
@Getter
public class MyCareerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토큰 subject 와 같은 값("kakao@{id}" 또는 "local@{id}") */
    @Column(nullable = false)
    private String userId;

    /** company | project | education | certificate | ... */
    @Column(nullable = false, length = 20)
    private String entryType;

    @Column(length = 100)
    private String company;

    /** 회사 행이면 직무 한 줄, 프로젝트 행이면 프로젝트명 */
    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 100)
    private String team;

    @Column(length = 100)
    private String role;

    private LocalDate startedOn;

    /** 비어 있으면 재직 중·진행 중 */
    private LocalDate endedOn;

    @Column(columnDefinition = "TEXT")
    private String summary;

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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static MyCareerEntry create(String userId) {
        MyCareerEntry entry = new MyCareerEntry();
        entry.userId = userId;
        return entry;
    }

    /** 고칠 수 있는 칸을 통째로 바꾼다. 검사·정리는 {@link CareerInput} 이 끝낸 값이다 */
    public void apply(CareerInput in) {
        this.entryType = in.entryType();
        this.company = in.company();
        this.title = in.title();
        this.team = in.team();
        this.role = in.role();
        this.startedOn = in.startedOn();
        this.endedOn = in.endedOn();
        this.summary = in.summary();
        this.description = in.description();
        this.achievements = in.achievements();
        this.techStack = in.techStack();
        this.tags = in.tags();
        this.referenceLinks = in.referenceLinks();
    }
}
