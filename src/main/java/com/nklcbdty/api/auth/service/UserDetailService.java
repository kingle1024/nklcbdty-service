package com.nklcbdty.api.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserDetailService {
    UserDetails loadUserById(String userId, String username, String refreshToken) throws UsernameNotFoundException;
}
