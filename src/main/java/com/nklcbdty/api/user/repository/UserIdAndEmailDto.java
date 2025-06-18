package com.nklcbdty.api.user.repository;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserIdAndEmailDto {
    String userId;
    String email;
}
