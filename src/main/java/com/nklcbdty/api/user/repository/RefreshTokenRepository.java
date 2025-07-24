package com.nklcbdty.api.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.user.vo.RefreshTokenVo;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenVo, Long> {

}
