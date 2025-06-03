package com.nklcbdty.api.user.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSettingsRequest {
    private String email;
    private List<String> subscribedServices;
    private List<String> selectedJobRoles;
}
