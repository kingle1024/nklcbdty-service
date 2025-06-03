package com.nklcbdty.api.user.service;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nklcbdty.api.user.dto.UserResponseDto;
import com.nklcbdty.api.user.repository.UserRepository;
import com.nklcbdty.api.auth.service.UserDetailService;
import com.nklcbdty.api.user.vo.UserVo;

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

    public UserResponseDto findByUserId(String userId) {
        UserVo user = userRepository.findByUserId(userId);
        return UserResponseDto.builder()
            .username(user.getUsername())
            .email(user.getEmail())
            .build();
    }
}
