package com.nklcbdty.api.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.auth.vo.UserVo;

public interface UserRepository extends JpaRepository<UserVo, String> {
    UserVo findByUserId(String username);
    UserVo save(UserVo user);
}
