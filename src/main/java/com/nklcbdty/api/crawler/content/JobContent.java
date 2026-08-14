package com.nklcbdty.api.crawler.content;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공고 본문. {@code job_mst} 와 1:1 이며 PK 를 그대로 공유한다.
 *
 * <p>{@code job_mst} 에 컬럼을 붙이지 않고 별도 테이블로 뺀 이유가 두 가지 있다.
 * <ul>
 *   <li>{@code Job_mst} 는 nklcbdty-common jar 안에 있어 컬럼을 늘리려면 common 버전을 올리고
 *       service·batch 를 함께 맞춰야 한다. 본문 수집은 service 안에서만 끝나는 일이다.</li>
 *   <li>본문은 수십 KB 라, 목록 조회(전 행 SELECT)에 딸려오면 손해다. 분리해 두면 필요할 때만 읽는다.</li>
 * </ul>
 */
@Entity
@Table(name = "job_content")
@Getter
@Setter
@NoArgsConstructor
public class JobContent {

    /** job_mst.id. 별도 시퀀스를 두지 않고 공유한다. */
    @Id
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "company_cd", length = 50)
    private String companyCd;

    // 실제 컬럼 타입(MEDIUMTEXT)은 JobContentSchemaInitializer 가 정한다.
    // ddl-auto=none 이라 JPA 는 테이블을 만들지 않으므로, 여기서는 @Lob 으로 "긴 문자열"만 알려주면 된다.
    // columnDefinition 에 MEDIUMTEXT 를 박으면 테스트용 H2 에서 DDL 이 깨진다.

    /** 태그를 걷어낸 본문 텍스트. 검색·임베딩은 이 값을 쓴다. */
    @Lob
    @Column(name = "content")
    private String content;

    /** 원본 HTML. 화면에 그대로 보여줄 때를 위해 남긴다(원본이 텍스트면 null). */
    @Lob
    @Column(name = "content_html")
    private String contentHtml;

    /** content 의 SHA-256. 재수집 때 내용이 그대로면 UPDATE 를 만들지 않기 위한 것. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 어느 fetcher 가 가져왔는지. 수집 방식이 바뀌었을 때 원인 추적용. */
    @Column(name = "source", length = 60)
    private String source;

    /** 수집 실패 사유. 성공하면 null 로 지운다. */
    @Column(name = "fail_reason", length = 300)
    private String failReason;

    /** 마지막 수집 시도 시각. 성공·실패 모두 갱신한다(실패한 공고를 매분 다시 때리지 않기 위함). */
    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public JobContent(Long jobId, String companyCd) {
        this.jobId = jobId;
        this.companyCd = companyCd;
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
