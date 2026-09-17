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

    /**
     * 카카오 계정 이메일을 user 행에 채운다(비었거나 달라진 경우에만).
     * 토큰 갱신(AuthService)에서는 이메일로 관리자 여부를 다시 판정하므로, 로그인 때 채워 둬야
     * 갱신 이후에도 관리자로 남는다. 카카오는 이메일 동의를 안 하면 null 이라 그때는 건드리지 않는다.
     */
    public void updateEmail(String userId, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        UserVo user = userRepository.findByUserId(userId);
        if (user == null || email.equalsIgnoreCase(user.getEmail())) {
            return;
        }
        user.setEmail(email);
        userRepository.save(user);
    }
}
