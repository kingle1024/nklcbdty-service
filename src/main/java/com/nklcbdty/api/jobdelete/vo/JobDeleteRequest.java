package com.nklcbdty.api.jobdelete.vo;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 채용공고 삭제요청. 사용자가 삭제요청을 하면 즉시 삭제되지 않고 PENDING 상태로 쌓이며,
 * 관리자가 승인(APPROVED, 실제 공고 삭제)/반려(REJECTED) 처리한다.
 */
@Entity
@Table(name = "job_delete_request")
@Data
public class JobDeleteRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 공고(job_mst) PK */
    @Column(nullable = false)
    private Long jobId;

    // 공고 스냅샷 — 공고가 삭제돼도 관리자가 무엇이었는지 알 수 있도록 보관
    @Column(nullable = true)
    private String annoId;

    @Column(nullable = true, length = 500)
    private String annoSubject;

    @Column(nullable = true)
    private String companyCd;

    @Column(nullable = true)
    private String sysCompanyCdNm;

    /** 삭제요청 사유(선택) */
    @Column(nullable = true, length = 1000)
    private String reason;

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 요청자 식별(로그인 사용자 id, 없으면 null) */
    @Column(nullable = true)
    private String requesterId;

    /** 요청자 IP */
    @Column(nullable = true, length = 64)
    private String requesterIp;

    /** 처리한 관리자 */
    @Column(nullable = true)
    private String processedBy;

    @CreationTimestamp
    private LocalDateTime insertDts;

    /** 승인/반려 처리 시각 */
    @Column(nullable = true)
    private LocalDateTime processDts;
}
