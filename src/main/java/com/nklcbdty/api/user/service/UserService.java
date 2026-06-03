package com.nklcbdty.api.user.service;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.auth.service.UserDetailService;
import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.user.service.BaseUserService;
import com.nklcbdty.common.vo.UserVo;

@Service
public class UserService extends BaseUserService implements UserDetailService {

    @Autowired
    public UserService(UserRepository userRepository) {
        super(userRepository);
    }

    @Override
    public UserDetails loadUserById(String userId, String username, String refreshToken) throws
        UsernameNotFoundException {
        UserVo user = userRepository.findByUserId(userId);
        String paramUserName;

        if (user == null) {
            userRepository.save(UserVo.builder()
                .userId(userId)
                .username(username)
                .build());
            paramUserName = username;
        } else {
            paramUserName = user.getUsername();
        }

        return new org.springframework.security.core.userdetails.User(
            paramUserName,
            "user.getPassword()",
            new ArrayList<>()
        );
    }
}
