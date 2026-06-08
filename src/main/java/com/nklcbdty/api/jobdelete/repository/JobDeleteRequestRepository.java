package com.nklcbdty.api.jobdelete.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.jobdelete.vo.JobDeleteRequest;

public interface JobDeleteRequestRepository extends JpaRepository<JobDeleteRequest, Long> {

    /** 특정 공고에 대해 해당 상태의 요청이 존재하는지 */
    boolean existsByJobIdAndStatus(Long jobId, String status);

    /** 특정 공고의 해당 상태 요청 1건 (중복 요청 방지/멱등 처리용) */
    Optional<JobDeleteRequest> findFirstByJobIdAndStatus(Long jobId, String status);

    /** 상태별 목록 (관리자 화면) */
    List<JobDeleteRequest> findByStatusOrderByInsertDtsDesc(String status);

    /** 전체 목록 (관리자 화면) */
    List<JobDeleteRequest> findAllByOrderByInsertDtsDesc();

    /** 해당 상태인 요청들의 jobId 목록 (프론트 버튼 상태용) */
    @Query("select r.jobId from JobDeleteRequest r where r.status = :status")
    List<Long> findJobIdsByStatus(@Param("status") String status);
}
