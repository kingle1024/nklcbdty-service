package com.nklcbdty.api.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklcbdty.common.vo.UserVo;

/**
 * user 테이블을 이메일로 조회하기 위한 리포지터리.
 *
 * <p>공통 모듈의 {@code UserRepository} 에는 이메일 조회가 없어서, 같은 엔티티를 가리키는
 * 리포지터리를 서비스 쪽에 하나 더 둔다(공통 jar 을 올리지 않아도 되도록).
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserVo, Long> {

    /** 같은 이메일을 쓰는 계정들. 먼저 만들어진 계정부터. */
    List<UserVo> findByEmailOrderByIdAsc(String email);
}
