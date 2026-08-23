package com.nklcbdty.api.troubleshooting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nklcbdty.api.troubleshooting.vo.TroubleshootingNote;

public interface TroubleshootingNoteRepository extends JpaRepository<TroubleshootingNote, Long> {

    Optional<TroubleshootingNote> findBySlug(String slug);

    /**
     * 목록 조회. 네 조건(검색어/프로젝트/심각도/태그)은 <b>빈 문자열이면 무시된다</b>.
     * {@code :param is null} 대신 빈 문자열을 안 쓴다는 표시로 쓰는 이유는, 타입 없는 null 을
     * 비교하는 JPQL 이 방언에 따라 파라미터 타입을 못 정해 실패하기 때문이다. 서비스에서
     * null 을 빈 문자열로 바꿔 넘긴다.
     *
     * <p>검색어는 화면에 보이는 요약뿐 아니라 본문(증상·원인·해결)과 태그·기술스택까지 훑는다.
     * 기록을 다시 찾는 실마리가 보통 에러 메시지 원문이나 태그이기 때문이다.
     *
     * <p>태그는 쉼표로 이어붙인 한 칼럼이라 정확히 일치시킬 수 없다. 저장된 값에 쉼표 뒤 공백이
     * 있는 것과 없는 것이 섞여 있어서, 공백을 지운 문자열 앞뒤에 쉼표를 덧붙이고 {@code %,tag,%}
     * 를 맞춘다. 이렇게 하면 {@code batch} 가 {@code batch-window} 를 잘못 잡지 않는다.
     */
    @Query("select n from TroubleshootingNote n "
         + "where (:keyword = '' "
         + "    or lower(n.title)      like lower(concat('%', :keyword, '%')) "
         + "    or lower(n.project)    like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.component, ''))  like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.errorCode, ''))  like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.symptom, ''))    like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.rootCause, ''))  like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.resolution, '')) like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.lesson, ''))     like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.techStack, ''))  like lower(concat('%', :keyword, '%')) "
         + "    or lower(coalesce(n.tags, ''))       like lower(concat('%', :keyword, '%'))) "
         + "  and (:project = '' or n.project = :project) "
         + "  and (:severity = '' or lower(coalesce(n.severity, '')) = :severity) "
         + "  and (:tag = '' "
         + "    or concat(',', lower(replace(coalesce(n.tags, ''), ' ', '')), ',') "
         + "       like concat('%,', :tag, ',%'))")
    Page<TroubleshootingNote> search(@Param("keyword") String keyword,
                                     @Param("project") String project,
                                     @Param("severity") String severity,
                                     @Param("tag") String tag,
                                     Pageable pageable);

    /** 필터 목록에 쓸 프로젝트 이름. 기록이 많은 프로젝트가 위로 온다 */
    @Query("select n.project from TroubleshootingNote n "
         + "group by n.project order by count(n.id) desc, n.project asc")
    List<String> findProjectsByFrequency();

    /** 태그 필터를 만들 때 쓰는 원본. 쉼표를 푸는 일은 서비스에서 한다 */
    @Query("select n.tags from TroubleshootingNote n where n.tags is not null and n.tags <> ''")
    List<String> findAllTagStrings();

    /** 심각도별 건수(요약 카드용). 값이 비어 있는 기록은 세지 않는다 */
    @Query("select lower(n.severity), count(n.id) from TroubleshootingNote n "
         + "where n.severity is not null and n.severity <> '' group by lower(n.severity)")
    List<Object[]> countBySeverity();
}
