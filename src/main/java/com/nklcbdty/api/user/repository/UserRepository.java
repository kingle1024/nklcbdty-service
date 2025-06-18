package com.nklcbdty.api.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.user.vo.UserVo;

public interface UserRepository extends JpaRepository<UserVo, String> {
    UserVo findByUserId(String username);
    UserVo save(UserVo user);
    List<UserVo> findByUserIdIn(List<String> userIds);
}
