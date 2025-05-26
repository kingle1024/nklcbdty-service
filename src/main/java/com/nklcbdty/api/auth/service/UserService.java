package com.nklcbdty.api.auth.service;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.auth.repository.UserRepository;
import com.nklcbdty.api.auth.vo.UserVo;

@Service
public class UserService implements UserDetailService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserById(String userId, String username, String refreshToken) throws
        UsernameNotFoundException {
        UserVo user = userRepository.findByUserId(userId);
        UserDetails userDetails;
        if(user == null) {
            userRepository.save(UserVo.builder()
                .userId(userId)
                .username(username)
                .refreshToken(refreshToken)
                .build());
            userDetails = new org.springframework.security.core.userdetails.User(
                username, "user.getPassword()", new ArrayList<>()
            );

        } else {
            userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(), "user.getPassword()", new ArrayList<>());
        }

        return userDetails;
    }
}
