package com.nklcbdty.api.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.user.vo.RefreshTokenVo;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenVo, Long> {

    /** 검증용: 이 사용자에게 이 토큰(해시)이 발급된 적 있는지. 회전 직후 재검증을 위해 무효화된 행도 찾는다. */
    Optional<RefreshTokenVo> findFirstByUserIdAndTokenOrderByIdDesc(String userId, String token);

    /** 회전용: 방금 쓴 옛 토큰 중 아직 무효화되지 않은 행. */
    Optional<RefreshTokenVo> findFirstByUserIdAndTokenAndIsRevokedFalseOrderByIdDesc(String userId, String token);
}
