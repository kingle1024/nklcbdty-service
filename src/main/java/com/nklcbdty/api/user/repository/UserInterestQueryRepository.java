package com.nklcbdty.api.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklcbdty.common.vo.UserInterestVo;

/**
 * user_interest 를 itemType 단위로 다루기 위한 리포지터리.
 *
 * <p>공통 모듈의 {@code UserInterestRepository} 에는 itemType 단위 삭제/정렬 조회가 없어서
 * 같은 엔티티를 가리키는 리포지터리를 서비스 쪽에 하나 더 둔다.
 */
@Repository
public interface UserInterestQueryRepository extends JpaRepository<UserInterestVo, Long> {

    /** 오래된 것부터. "가장 최근 값" 을 골라야 할 때 순서를 보장한다. */
    List<UserInterestVo> findByUserIdAndItemTypeOrderByIdAsc(String userId, String itemType);

    void deleteByUserIdAndItemType(String userId, String itemType);
}
