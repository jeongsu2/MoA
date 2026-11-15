package com.moa.auth;

import com.moa.user.Role;
import com.moa.user.User;

public record SignupResponse(Long userId, Role role) {

    static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getRole());
    }
}
